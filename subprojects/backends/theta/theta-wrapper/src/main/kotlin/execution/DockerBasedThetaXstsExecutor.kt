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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

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

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification) = coroutineScope {
        val container = createContainer(thetaExecutionSpecification)

        val result = try {
            runContainer(thetaExecutionSpecification, container)
        } finally {
            withContext(NonCancellable) {
                stopContainer(container)
                saveContainerLogs(thetaExecutionSpecification, container)
                destroyContainer(container)
            }
        }

        ThetaExecutionResult(result.statusCode)
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
            runAsync(Dispatchers.IO) {
                dockerClient.waitContainerCmd(container.id).exec(it)
            }
        }
    }

    private fun stopContainer(container: CreateContainerResponse) {
        try {
            dockerClient.stopContainerCmd(container.id).exec()
        } catch (_: Exception) {
            // swallow exceptions
        }
    }

    private suspend fun saveContainerLogs(thetaExecutionSpecification: ThetaExecutionSpecification, container: CreateContainerResponse) = withContext(Dispatchers.IO) {
        try {
            dockerClient.logContainerCmd(container.id)
                .withStdOut(true)
                .withStdErr(true)
                .withTailAll()
                .exec(StreamLoggerCallback(thetaExecutionSpecification.logStream, thetaExecutionSpecification.errorStream))
                .await()
        } catch (t: Throwable) {
            // swallow exceptions
        }
    }

    private fun destroyContainer(container: CreateContainerResponse) {
        try {
            dockerClient.removeContainerCmd(container.id).withForce(true).exec()
        } catch (_: Exception) {
            // swallow exceptions
        }
    }

}
