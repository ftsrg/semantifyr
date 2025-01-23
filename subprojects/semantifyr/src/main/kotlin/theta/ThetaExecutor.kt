/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.theta

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.StreamLoggerCallback
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.awaitAny
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.runAsync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.time.toDuration
import kotlin.time.toDurationUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ThetaRuntimeDetails(
    val id: Int,
    val workingDirectory: String,
    val name: String
) {
    val modelFile = "$name.xsts"
    val cexFile = "$name$id.cex"
    val cexsFile = "$name.cexs"
    val logFile = "theta$id.out"
    val errFile = "theta$id.err"

    val modelPath = "$workingDirectory${File.separator}$modelFile"
    // Theta can produce several kinds of output files, even images
    // it's probably a good idea to group all of those under here
    val outputPath = "$workingDirectory${File.separator}traces"

    val cexPath = "$outputPath${File.separator}$cexFile"
    val cexsPath = "$outputPath${File.separator}$cexsFile"
    val logPath = "$workingDirectory${File.separator}$logFile"
    val errPath = "$workingDirectory${File.separator}$errFile"

    val isUnsafe: Boolean by lazy {
        Files.exists(Paths.get(cexPath))
    }
}

abstract class ThetaExecutor {
    abstract fun run(workingDirectory: String, name: String) : ThetaRuntimeDetails

    // Some Theta parameters are only important to Theta (e.g., what abstraction to use),
    // but some are important here as well, such as if the output is a single or several .cex files, or .cexs files, etc. (see TRACEGEN).
    // To keep the different executors uniform, they should use this helper function to get their Theta parameters.
    fun getParameters(parameter: String, thetaRuntimeDetails: ThetaRuntimeDetails, docker: Boolean = false): Array<String> {
        val params = parameter.split(" ").toTypedArray()
        val outputFlag = if(parameter.contains("TRACEGEN")) {
            if(docker) {
                "--trace-dir " + "/host/${File(thetaRuntimeDetails.cexsPath).parent}"
            } else {
                "--trace-dir " + File(thetaRuntimeDetails.cexsPath).parent
            }
        } else {
            if(docker) {
                "--cexfile " + "/host/${thetaRuntimeDetails.cexFile}"
            } else {
                "--cexfile " + thetaRuntimeDetails.cexFile
            }
        }
        val modelFlag = if(docker) {
            "--model " + "/host/${thetaRuntimeDetails.modelFile}"
        } else {
            "--model " + thetaRuntimeDetails.modelFile
        }
            return (params + outputFlag + modelFlag).joinToString(" ").split(" ").toTypedArray()
    }
}

class ThetaDockerExecutor(
    private val version: String,
    private val parameters: List<String>,
    private val timeout: Long = 3,
    private val timeUnit: TimeUnit = TimeUnit.MINUTES
) : ThetaExecutor() {

    private val logger by loggerFactory()

    private val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    private val httpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(config.dockerHost)
        .sslConfig(config.sslConfig).build()
    private val dockerClient = DockerClientImpl.getInstance(config, httpClient);

    fun initTheta() {
        dockerClient.pullImageCmd("ftsrg/theta-xsts-cli").withTag(version).start().awaitCompletion()
    }

    private suspend fun runTheta(
        workingDirectory: String,
        name: String,
        parameter: String,
        id: Int
    ): ThetaRuntimeDetails {
        logger.info("Starting theta ($id)")

        val thetaRuntimeDetails = ThetaRuntimeDetails(id, workingDirectory, name)

        val container = createContainer(thetaRuntimeDetails, parameter)

        dockerClient.startContainerCmd(container.id).exec()

        val result = try {
            withTimeout(timeout.toDuration(timeUnit.toDurationUnit())) {
                runAsync<WaitResponse> {
                    dockerClient.waitContainerCmd(container.id).exec(it)
                }
            }
        } catch (e: TimeoutCancellationException) {
            logger.info("Theta timed out ($id)")
            throw e
        } catch (e: CancellationException) {
            logger.info("Theta cancelled ($id)")
            throw e
        } finally {
            withContext(NonCancellable) {
                logger.debug("Saving theta logs ($id)")
                saveContainerLogs(thetaRuntimeDetails, container)

                logger.debug("Removing theta ($id)")
                dockerClient.removeContainerCmd(container.id).withForce(true).exec()
            }
        }

        if (result.statusCode == 0) {
            logger.info("Theta finished ($id)")
        } else {
            logger.error("Theta failed ($id)")
            throw IllegalStateException("Theta execution failed with code ${result.statusCode}. See ${thetaRuntimeDetails.errPath}")
        }

        return thetaRuntimeDetails
    }

    private suspend fun saveContainerLogs(
        thetaRuntimeDetails: ThetaRuntimeDetails,
        container: CreateContainerResponse
    ) {
        try {
            val logFile = File(thetaRuntimeDetails.logPath)
            val errFile = File(thetaRuntimeDetails.errPath)

            dockerClient.logContainerCmd(container.id)
                .withStdOut(true)
                .withStdErr(true)
                .withTailAll()
                .exec(StreamLoggerCallback(logFile.outputStream(), errFile.outputStream()))
                .await()
        } catch (e: Throwable) {
            logger.error("Exception during saving logging details (${thetaRuntimeDetails.id})", e)
        }
    }

    private fun createContainer(
        thetaRuntimeDetails: ThetaRuntimeDetails,
        parameter: String
    ): CreateContainerResponse {
        val hostConfig = HostConfig.newHostConfig()
            .withBinds(Bind(thetaRuntimeDetails.workingDirectory, Volume("/host")))

        val param = super.getParameters(parameter, thetaRuntimeDetails, true)

        val container = dockerClient.createContainerCmd("ftsrg/theta-xsts-cli:$version")
            .withCmd(
                *param
            )
            .withHostConfig(hostConfig)
            .exec()

        return container
    }

    private suspend fun runWorkflow(workingDirectory: String, name: String) = supervisorScope {
        val jobs = parameters.indices.map { index ->
            async(Dispatchers.IO) {
                runTheta(workingDirectory, name, parameters[index], index)
            }
        }

        try {
            logger.debug("Awaiting jobs")
            jobs.awaitAny()
        } finally {
            logger.debug("Canceling jobs")
            jobs.forEach {
                it.cancelAndJoin()
            }
        }
    }

    override
    fun run(workingDirectory: String, name: String) = runBlocking {
        val absoluteDirectory = Path.of(workingDirectory).absolute().toString()

        runWorkflow(absoluteDirectory, name)
    }

}

class ThetaShellExecutor(
    private val shPath: String,
    private val parameters: List<String>,
    private val timeout: Long = 3,
    private val timeUnit: TimeUnit = TimeUnit.MINUTES
) : ThetaExecutor() {

    private val logger by loggerFactory()
    private val mutex = Mutex()

    init {
        if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            throw UnsupportedOperationException("ThetaShellExecutor does not support Windows for now")
        }
    }

    private suspend fun runTheta(
        workingDirectory: String,
        name: String,
        parameter: String,
        id: Int
    ): ThetaRuntimeDetails {
        logger.info("Starting theta ($id)")

        val thetaRuntimeDetails = ThetaRuntimeDetails(id, workingDirectory, name)

        val param = super.getParameters(parameter, thetaRuntimeDetails, false)

        val processBuilder = ProcessBuilder(
            shPath,
            *param
        )
        processBuilder.directory(File(workingDirectory))

        val process = try {
            withTimeout(timeout.toDuration(timeUnit.toDurationUnit())) {
                mutex.withLock {
                    processBuilder.start()
                }
            }
        } catch (e: TimeoutCancellationException) {
            logger.info("Theta timed out ($id)")
            throw e
        } catch (e: Exception) {
            logger.error("Theta execution failed ($id)", e)
            throw e
        }

        val exitCode = try {
            withTimeout(timeout.toDuration(timeUnit.toDurationUnit())) {
                process.waitFor()
            }
        } catch (e: TimeoutCancellationException) {
            logger.info("Theta timed out ($id)")
            process.destroy()
            throw e
        } finally {
            saveProcessLogs(thetaRuntimeDetails, process)
        }

        if (exitCode == 0) {
            logger.info("Theta finished ($id)")
        } else {
            logger.error("Theta failed ($id) with code $exitCode")
            throw IllegalStateException("Theta execution failed with code $exitCode. See ${thetaRuntimeDetails.errPath}")
        }

        return thetaRuntimeDetails
    }

    private fun saveProcessLogs(thetaRuntimeDetails: ThetaRuntimeDetails, process: Process) {
        try {
            val logFile = File(thetaRuntimeDetails.logPath)
            val errFile = File(thetaRuntimeDetails.errPath)

            process.inputStream.bufferedReader().use { logFile.writeText(it.readText()) }
            process.errorStream.bufferedReader().use { errFile.writeText(it.readText()) }
        } catch (e: Exception) {
            logger.error("Exception during saving logging details (${thetaRuntimeDetails.id})", e)
        }
    }

    private suspend fun runWorkflow(workingDirectory: String, name: String) = supervisorScope {
        val jobs = parameters.indices.map { index ->
            async(Dispatchers.IO) {
                runTheta(workingDirectory, name, parameters[index], index)
            }
        }

        try {
            logger.debug("Awaiting jobs")
            jobs.awaitAny()
        } finally {
            logger.debug("Canceling jobs")
            jobs.forEach {
                it.cancelAndJoin()
            }
        }
    }

    override
    fun run(workingDirectory: String, name: String) = runBlocking {
        val absoluteDirectory = Path.of(workingDirectory).absolute().toString()

        runWorkflow(absoluteDirectory, name)
    }
}
