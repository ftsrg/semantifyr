/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.compiler

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
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
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

class ThetaRuntimeDetails(
    val id: Int,
    val workingDirectory: String,
    val name: String
) {
    val modelFile = "$name.xsts"
    val cexFile = "$name$id.cex"
    val logFile = "theta$id.out"
    val errFile = "theta$id.err"

    val modelPath = "$workingDirectory${File.separator}$modelFile"
    val cexPath = "$workingDirectory${File.separator}$cexFile"
    val logPath = "$workingDirectory${File.separator}$logFile"
    val errPath = "$workingDirectory${File.separator}$errFile"

    val isUnsafe: Boolean by lazy {
        Files.exists(Paths.get(cexPath))
    }
}

class ThetaExecutor(
    private val version: String = "latest",
    private val parameters: List<String>,
    private val timeout: Long = 3,
    private val timeUnit: TimeUnit = TimeUnit.MINUTES
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    private val httpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(config.getDockerHost())
        .sslConfig(config.getSSLConfig()).build()
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
            throw IllegalStateException("Theta execution failed with code ${result.statusCode}. See $thetaRuntimeDetails")
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

        val container = dockerClient.createContainerCmd("ftsrg/theta-xsts-cli:$version")
            .withCmd(
                "CEGAR",
                "--model", "/host/${thetaRuntimeDetails.modelFile}",
                "--cexfile", "/host/${thetaRuntimeDetails.cexFile}",
                *parameter.split(" ").toTypedArray(),
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

    fun run(workingDirectory: String, name: String) = runBlocking {
        val absoluteDirectory = Path.of(workingDirectory).absolute().toString()

        runWorkflow(absoluteDirectory, name)
    }

}
