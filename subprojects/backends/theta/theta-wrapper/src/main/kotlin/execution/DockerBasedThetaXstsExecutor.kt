/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.wrapper.execution

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
import kotlin.coroutines.cancellation.CancellationException

class DockerBasedThetaXstsExecutor : ThetaXstsExecutor() {

    private val imageName = "ftsrg/theta-xsts-cli"
    private val version = "6.27.0"
    private val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    private val httpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(config.dockerHost)
        .sslConfig(config.sslConfig).build()
    private val dockerClient = DockerClientImpl.getInstance(config, httpClient);

    override fun initialize() {
        val imageId = "$imageName:$version"
        try {
            dockerClient.inspectImageCmd(imageId).exec()
        } catch (e: NotFoundException) {
            dockerClient.pullImageCmd(imageName).withTag(version).start().awaitCompletion()
        }
    }

    override fun check(): Boolean {
         try {
             dockerClient.infoCmd().exec()

             return true
         } catch (_: Exception) {
             return false
         }
    }

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult {
        val container = createContainer(thetaExecutionSpecification)

        val result = try {
            runContainer(thetaExecutionSpecification, container)
        } catch (e: TimeoutCancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } finally {
            withContext(NonCancellable) {
                saveContainerLogs(thetaExecutionSpecification, container)

                dockerClient.removeContainerCmd(container.id).withForce(true).exec()
            }
        }

        return ThetaExecutionResult(result.statusCode)
    }

    private fun createContainer(thetaExecutionSpecification: ThetaExecutionSpecification): CreateContainerResponse {
        val hostConfig = HostConfig.newHostConfig()
            .withBinds(Bind(thetaExecutionSpecification.workingDirectory.absolutePath, Volume("/host/working_directory")))

        val container = dockerClient.createContainerCmd("$imageName:$version")
            .withHostConfig(hostConfig)
            .withWorkingDir("/host/working_directory")
            .withCmd(thetaExecutionSpecification.command)
            .withEntrypoint("java", "-jar", "/theta-xsts-cli.jar")
            .exec()

        return container
    }

    private suspend fun runContainer(thetaExecutionSpecification: ThetaExecutionSpecification, container: CreateContainerResponse): WaitResponse {
        dockerClient.startContainerCmd(container.id).exec()

        return withTimeout(thetaExecutionSpecification) {
            runAsync {
                dockerClient.waitContainerCmd(container.id).exec(it)
            }
        }
    }

    private suspend fun saveContainerLogs(thetaExecutionSpecification: ThetaExecutionSpecification, container: CreateContainerResponse) {
        try {
            dockerClient.logContainerCmd(container.id)
                .withStdOut(true)
                .withStdErr(true)
                .withTailAll()
                .exec(StreamLoggerCallback(thetaExecutionSpecification.logStream, thetaExecutionSpecification.errorStream))
                .await()
        } catch (t: Throwable) {
            // NO-OP
        }
    }

}
