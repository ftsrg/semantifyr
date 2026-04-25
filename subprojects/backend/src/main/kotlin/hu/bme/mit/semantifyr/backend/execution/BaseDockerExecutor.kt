/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.apache.commons.io.output.NullOutputStream
import org.slf4j.Logger
import java.io.File
import java.io.FileOutputStream

abstract class BaseDockerExecutor(
    protected val image: String,
) {

    protected abstract val logger: Logger

    protected val dockerClient by lazy {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
        val httpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .build()
        DockerClientImpl.getInstance(config, httpClient)
    }

    open fun isAvailable(): Boolean {
        try {
            dockerClient.pingCmd().exec()
        } catch (throwable: Throwable) {
            logger.debug { "Docker daemon is unreachable: $throwable" }

            return false
        }

        logger.debug { "Docker daemon reachable" }
        return true
    }

    protected fun ensureImagePresent() {
        try {
            dockerClient.inspectImageCmd(image).exec()
            logger.debug { "Docker image $image already present locally" }
        } catch (_: NotFoundException) {
            val (repository, tag) = splitImageRef(image)
            logger.info { "Pulling Docker image $repository:$tag (not present locally)" }
            dockerClient.pullImageCmd(repository)
                .withTag(tag)
                .start()
                .awaitCompletion()
            logger.info { "Pulled Docker image $repository:$tag" }
        }
    }

    protected suspend fun runContainer(
        container: CreateContainerResponse,
        logFile: File? = null,
        errorFile: File? = null,
    ): WaitResponse {
        return DockerContainerTracker.tracking(dockerClient, container.id) {
            try {
                startAndWait(container)
            } finally {
                withContext(NonCancellable) {
                    stopContainer(container)
                    saveContainerLogs(container, logFile, errorFile)
                    destroyContainer(container)
                }
            }
        }
    }

    private suspend fun startAndWait(container: CreateContainerResponse): WaitResponse {
        dockerClient.startContainerCmd(container.id).exec()
        return runAsync(Dispatchers.IO) { callback ->
            dockerClient.waitContainerCmd(container.id).exec(callback)
        }
    }

    private fun stopContainer(container: CreateContainerResponse) {
        try {
            dockerClient.stopContainerCmd(container.id).exec()
            logger.debug { "Stopped Docker container ${container.id}" }
        } catch (e: Exception) {
            logger.debug { "Stop of Docker container ${container.id} failed (likely already exited): $e" }
        }
    }

    private fun destroyContainer(container: CreateContainerResponse) {
        try {
            dockerClient.removeContainerCmd(container.id).withForce(true).exec()
        } catch (e: Exception) {
            logger.debug { "Best-effort remove of Docker container ${container.id} failed: $e" }
        }
    }

    private suspend fun saveContainerLogs(
        container: CreateContainerResponse,
        logFile: File?,
        errorFile: File?,
    ) = withContext(Dispatchers.IO) {
        val logStream = logFile?.let { FileOutputStream(it, true) } ?: NullOutputStream.INSTANCE
        val errorStream = errorFile?.let { FileOutputStream(it, true) } ?: NullOutputStream.INSTANCE
        try {
            dockerClient.logContainerCmd(container.id)
                .withStdOut(true)
                .withStdErr(true)
                .withTailAll()
                .exec(StreamLoggerCallback(logStream, errorStream))
                .await()
        } catch (e: Throwable) {
            // Log streaming failures do not fail the verification run.
            logger.debug { "Failed to stream logs for Docker container ${container.id}: $e" }
        }
    }

    private fun splitImageRef(reference: String): Pair<String, String> {
        // Digest references (`repo@sha256:...`) are pulled by digest, not tag; treat the whole
        // thing as the repository and let docker-java handle it.
        if (reference.contains('@')) {
            return reference to "latest"
        }
        val colonIndex = reference.lastIndexOf(':')
        if (colonIndex >= 0 && !reference.substring(colonIndex + 1).contains('/')) {
            return reference.substring(0, colonIndex) to reference.substring(colonIndex + 1)
        }
        return reference to "latest"
    }
}
