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

/**
 * Shared plumbing for backends that invoke model checkers inside Docker containers. Subclasses
 * name the target [image], provide a [logger], and build the per-invocation container via
 * [createContainer] — everything else (daemon probe, image pull, lifecycle, log capture, stop +
 * remove, shutdown-hook cleanup) lives here.
 */
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

    /** Whether the Docker daemon is reachable. Lazy — does not pull or run anything. */
    open fun isAvailable(): Boolean {
        val available = runCatching { dockerClient.pingCmd().exec() }.isSuccess
        logger.debug { "Docker daemon reachable: $available" }
        return available
    }

    /** Pulls [image] if it isn't already present locally. Safe to call on every run. */
    protected fun ensureImagePresent() {
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

    /**
     * Runs [container] through its full lifecycle: registers it with [DockerContainerTracker] so a
     * JVM shutdown will clean it up, starts it, waits for exit, then stops + captures logs + removes
     * it under [NonCancellable]. Returns the container's exit status.
     *
     * Log streaming is best-effort: exceptions from the log callback are swallowed because losing
     * a few log lines is less important than finishing the teardown cleanly.
     */
    protected suspend fun runContainer(
        container: CreateContainerResponse,
        logFile: File? = null,
        errorFile: File? = null,
    ): WaitResponse {
        DockerContainerTracker.track(dockerClient, container.id)
        try {
            return try {
                startAndWait(container)
            } finally {
                withContext(NonCancellable) {
                    stopContainer(container)
                    saveContainerLogs(container, logFile, errorFile)
                    destroyContainer(container)
                }
            }
        } finally {
            DockerContainerTracker.untrack(dockerClient, container.id)
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
        } catch (_: Exception) {
            // Already exited — nothing to stop.
        }
    }

    private fun destroyContainer(container: CreateContainerResponse) {
        try {
            dockerClient.removeContainerCmd(container.id).withForce(true).exec()
        } catch (_: Exception) {
            // Best-effort cleanup.
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
        } catch (_: Throwable) {
            // Log streaming failures do not fail the verification run.
        }
    }

    /** Splits `repo[:tag]` with protection for host-prefixed repos (`host:5000/name`). */
    private fun splitImageRef(reference: String): Pair<String, String> {
        val colonIndex = reference.lastIndexOf(':')
        if (colonIndex >= 0 && !reference.substring(colonIndex + 1).contains('/')) {
            return reference.substring(0, colonIndex) to reference.substring(colonIndex + 1)
        }
        return reference to "latest"
    }
}
