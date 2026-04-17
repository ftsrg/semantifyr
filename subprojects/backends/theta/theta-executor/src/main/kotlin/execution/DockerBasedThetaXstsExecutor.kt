/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.execution

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionResult
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionSpecification
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorSpec
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutor
import hu.bme.mit.semantifyr.backends.theta.utils.StreamLoggerCallback
import hu.bme.mit.semantifyr.backends.theta.utils.runAsync
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.apache.commons.io.output.NullOutputStream
import java.io.File
import java.io.FileOutputStream

class DockerBasedThetaXstsExecutor(
    private val image: String = ThetaExecutorSpec.Docker.DEFAULT_IMAGE,
) : ThetaXstsExecutor() {

    override val logger by loggerFactory()

    private val dockerClient by lazy {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
        val httpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig).build()
        DockerClientImpl.getInstance(config, httpClient)
    }

    override fun isAvailable(): Boolean {
        val available = runCatching { dockerClient.pingCmd().exec() }.isSuccess
        logger.debug { "Docker daemon reachable: $available" }
        return available
    }

    private fun initialize() {
        try {
            dockerClient.inspectImageCmd(image).exec()
            logger.debug { "Docker image $image already present locally" }
        } catch (_: NotFoundException) {
            val (repository, tag) = splitImageRef(image)
            logger.info { "Pulling Docker image $repository:$tag (not present locally)" }
            dockerClient.pullImageCmd(repository).withTag(tag).start().awaitCompletion()
            logger.info { "Pulled Docker image $repository:$tag" }
        }
    }

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification) = coroutineScope {
        initialize()
        prepareOutputFiles(thetaExecutionSpecification)

        val container = createContainer(thetaExecutionSpecification)
        logger.info { "Starting Docker container for image $image in ${thetaExecutionSpecification.workingDirectory.absolutePath}" }
        logger.debug { "Docker container id ${container.id}, command: ${thetaExecutionSpecification.command.joinToString(" ")}" }

        val result = try {
            runContainer(container)
        } finally {
            withContext(NonCancellable) {
                stopContainer(container)
                saveContainerLogs(container, thetaExecutionSpecification.logFile, thetaExecutionSpecification.errorFile)
                destroyContainer(container)
            }
        }

        logger.info { "Docker container exited with status ${result.statusCode}" }

        ThetaExecutionResult(result.statusCode)
    }

    private fun stopContainer(container: CreateContainerResponse) {
        try {
            dockerClient.stopContainerCmd(container.id).exec()
            logger.debug { "Stopped Docker container ${container.id}" }
        } catch (_: Exception) {
            // swallow exceptions
        }
    }

    private fun createContainer(thetaExecutionSpecification: ThetaExecutionSpecification): CreateContainerResponse {
        val hostConfig = HostConfig.newHostConfig()
            .withBinds(Bind(thetaExecutionSpecification.workingDirectory.absolutePath, Volume("/host/working_directory")))

        return dockerClient.createContainerCmd(image)
            .withHostConfig(hostConfig)
            .withWorkingDir("/host/working_directory")
            .withCmd(thetaExecutionSpecification.command)
            .withEntrypoint("java", "-jar", "/theta-xsts-cli.jar")
            .exec()
    }

    private fun splitImageRef(reference: String): Pair<String, String> {
        val colonIndex = reference.lastIndexOf(':')
        if (colonIndex >= 0 && !reference.substring(colonIndex + 1).contains('/')) {
            return reference.substring(0, colonIndex) to reference.substring(colonIndex + 1)
        }
        return reference to "latest"
    }

    private suspend fun runContainer(container: CreateContainerResponse): WaitResponse {
        dockerClient.startContainerCmd(container.id).exec()

        return runAsync(Dispatchers.IO) {
            dockerClient.waitContainerCmd(container.id).exec(it)
        }
    }

    private suspend fun saveContainerLogs(container: CreateContainerResponse, logFile: File?, errorFile: File?) = withContext(Dispatchers.IO) {
        val logStream = logFile?.let { FileOutputStream(it, true) } ?: NullOutputStream.INSTANCE
        val errorStream = errorFile?.let { FileOutputStream(it, true) } ?: NullOutputStream.INSTANCE
        try {
            dockerClient.logContainerCmd(container.id)
                .withStdOut(true)
                .withStdErr(true)
                .withTailAll()
                .exec(StreamLoggerCallback(logStream, errorStream))
                .await()
        } catch (_: Throwable) {
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
