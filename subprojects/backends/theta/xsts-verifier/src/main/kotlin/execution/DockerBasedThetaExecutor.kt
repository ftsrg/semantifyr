/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.execution

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

class DockerBasedThetaExecutor : ThetaExecutor {
    private val version = "6.27.0"
    private val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    private val httpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(config.dockerHost)
        .sslConfig(config.sslConfig).build()
    private val dockerClient = DockerClientImpl.getInstance(config, httpClient);

    override fun initialize() {
        val imageId = "ftsrg/theta-xsts-cli:$version"
        try {
            dockerClient.inspectImageCmd(imageId).exec()
        } catch (e: NotFoundException) {
            dockerClient.pullImageCmd("ftsrg/theta-xsts-cli").withTag(version).start().awaitCompletion()
        }
    }

    override suspend fun execute(
        thetaRuntimeDetails: ThetaRuntimeDetails,
        parameter: String,
        timeout: Long,
        timeUnit: TimeUnit
    ): ThetaVerificationResult {
        val artifactsDir = File(thetaRuntimeDetails.workingDirectory, thetaRuntimeDetails.artifactsPath)
        artifactsDir.deleteRecursively()
        artifactsDir.mkdirs()

        val container = createContainer(thetaRuntimeDetails, parameter)

        dockerClient.startContainerCmd(container.id).exec()

        val result = try {
            withTimeout(timeout.toDuration(timeUnit.toDurationUnit())) {
                runAsync<WaitResponse> {
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

        return thetaRuntimeDetails.toVerificationResult()
    }

    private suspend fun saveContainerLogs(
        thetaRuntimeDetails: ThetaRuntimeDetails,
        container: CreateContainerResponse
    ) {
        try {
            val logFile = File(thetaRuntimeDetails.workingDirectory, thetaRuntimeDetails.logPath)
            val errFile = File(thetaRuntimeDetails.workingDirectory, thetaRuntimeDetails.errPath)

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
                *parameter.split(" ").toTypedArray(),
                "--model", "/host/${thetaRuntimeDetails.modelPath}",
                "--cexfile", "/host/${thetaRuntimeDetails.cexPath}",
            )
            .withHostConfig(hostConfig)
            .exec()

        return container
    }

}
