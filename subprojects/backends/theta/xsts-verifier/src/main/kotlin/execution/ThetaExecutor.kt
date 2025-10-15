/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.execution

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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

class ThetaRuntimeDetails(
    val workingDirectory: String,
    val name: String,
    val id: Int,
) {
    val modelFileName = "$name.xsts"
    val cexFileName = "out.cex"
    val logFileName = "theta.out"
    val errFileName = "theta.err"

    val modelPath = modelFileName
    val artifactsPath = "verification${File.separator}artifacts$id"
    val cexPath = "$artifactsPath${File.separator}$cexFileName"
    val logPath = "$artifactsPath${File.separator}$logFileName"
    val errPath = "$artifactsPath${File.separator}$errFileName"

    val isUnsafe: Boolean by lazy {
        Files.exists(Paths.get(workingDirectory, cexPath))
    }
    val isSafe: Boolean
        get() = !isUnsafe
}

class DockerBasedThetaExecutor(
    val version: String,
    val timeout: Long = 3,
    val timeUnit: TimeUnit = TimeUnit.MINUTES
) {

    private val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    private val httpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(config.dockerHost)
        .sslConfig(config.sslConfig).build()
    private val dockerClient = DockerClientImpl.getInstance(config, httpClient);

    fun initialize() {
        dockerClient.pullImageCmd("ftsrg/theta-xsts-cli").withTag(version).start().awaitCompletion()
    }

    suspend fun runTheta(
        workingDirectory: String,
        name: String,
        parameter: String,
        id: Int,
    ): ThetaRuntimeDetails {
        val thetaRuntimeDetails = ThetaRuntimeDetails(workingDirectory, name, id)

        val artifactsDir = File(thetaRuntimeDetails.workingDirectory, thetaRuntimeDetails.artifactsPath)
        artifactsDir.deleteRecursively()
        artifactsDir.mkdirs()

        val container = createContainer(thetaRuntimeDetails, parameter)

        dockerClient.startContainerCmd(container.id).exec()

        val result = try {
            withTimeout(timeout.toDuration(timeUnit.toDurationUnit())) {
                runAsync<WaitResponse>(Dispatchers.IO) {
                    dockerClient.waitContainerCmd(container.id).exec(it)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } finally {
            withContext(NonCancellable) {
                saveContainerLogs(thetaRuntimeDetails, container)

                dockerClient.removeContainerCmd(container.id).withForce(true).exec()
            }
        }

        if (result.statusCode != 0) {
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
        } catch (t: Throwable) {
            // NO-OP
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
                "--model", "/host/${thetaRuntimeDetails.modelPath}",
                "--cexfile", "/host/${thetaRuntimeDetails.cexPath}",
                *parameter.split(" ").toTypedArray(),
            )
            .withHostConfig(hostConfig)
            .exec()

        return container
    }

}

class ThetaPortfolioExecutor(
    version: String,
    val parameters: List<String>,
    timeout: Long = 3,
    timeUnit: TimeUnit = TimeUnit.MINUTES
) {

    private val thetaExecutor = DockerBasedThetaExecutor(version, timeout, timeUnit)

    fun initialize() {
        thetaExecutor.initialize()
    }

    private suspend fun runWorkflow(workingDirectory: String, name: String) = supervisorScope {
        val jobs = parameters.indices.map { index ->
            async(Dispatchers.IO) {
                thetaExecutor.runTheta(workingDirectory, name, parameters[index], index)
            }
        }

        try {
            jobs.awaitAny()
        } finally {
            jobs.forEach {
                it.cancelAndJoin()
            }
        }
    }

    private fun CoroutineScope.startCancellationChecker(progressContext: ProgressContext): Deferred<Unit> {
        return async {
            while (true) {
                try {
                    progressContext.checkIsCancelled()
                } catch (c: CancellationException) {
                    this@startCancellationChecker.coroutineContext.cancel(c)
                }
                delay(100)
            }
        }
    }

    fun run(workingDirectory: String, name: String, progressContext: ProgressContext) = runBlocking {
        val absoluteDirectory = Path.of(workingDirectory).absolute().toString()

        val cancellationChecker = startCancellationChecker(progressContext)

        val result = runWorkflow(absoluteDirectory, name)

        cancellationChecker.cancel()

        result
    }

}
