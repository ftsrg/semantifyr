/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.WaitResponse
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.apache.commons.io.output.NullOutputStream
import java.io.File
import java.io.FileOutputStream

abstract class DockerBasedBackendExecutor(
    protected val image: String,
    private val daemon: DockerDaemon = DockerDaemon.Process,
) : BackendExecutor {

    protected val logger by loggerFactory()

    protected val dockerClient: DockerClient
        get() = daemon.client

    override fun isAvailable(): Boolean {
        return daemon.isReachable
    }

    override fun prepare() {
        try {
            dockerClient.inspectImageCmd(image).exec()
            logger.debug { "Docker image $image already present locally" }
        } catch (_: NotFoundException) {
            val (repository, tag) = splitImageReference(image)
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
        prepareOutputFiles(logFile, errorFile)
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
        return runAsync(Dispatchers.IO) {
            dockerClient.waitContainerCmd(container.id).exec(it)
        }
    }

    private fun stopContainer(container: CreateContainerResponse) {
        try {
            dockerClient.stopContainerCmd(container.id).exec()
            logger.debug { "Stopped Docker container ${container.id}" }
        } catch (throwable: Throwable) {
            logger.debug(throwable) { "Stop of Docker container ${container.id} failed (likely already exited)." }
        }
    }

    private fun destroyContainer(container: CreateContainerResponse) {
        try {
            dockerClient.removeContainerCmd(container.id).withForce(true).exec()
        } catch (throwable: Throwable) {
            logger.debug(throwable) { "Best-effort remove of Docker container ${container.id} failed." }
        }
    }

    private suspend fun saveContainerLogs(
        container: CreateContainerResponse,
        logFile: File?,
        errorFile: File?,
    ) {
        val logStream = logFile?.let {
            FileOutputStream(it, true)
        } ?: NullOutputStream.INSTANCE

        val errorStream = errorFile?.let {
            FileOutputStream(it, true)
        } ?: NullOutputStream.INSTANCE

        try {
            dockerClient.logContainerCmd(container.id)
                .withStdOut(true)
                .withStdErr(true)
                .withTailAll()
                .exec(StreamLoggerCallback(logStream, errorStream))
                .await()
        } catch (throwable: Throwable) {
            // Log streaming failures do not fail the execution.
            logger.debug(throwable) { "Failed to stream logs for Docker container ${container.id}." }
        }
    }

    private fun splitImageReference(reference: String): ImageReference {
        if (reference.contains('@')) { // digest
            return ImageReference(reference, "latest")
        }
        val colonIndex = reference.lastIndexOf(':')
        if (colonIndex >= 0 && !reference.substring(colonIndex + 1).contains('/')) {
            return ImageReference(reference.substring(0, colonIndex), reference.substring(colonIndex + 1))
        }
        return ImageReference(reference, "latest")
    }

    private data class ImageReference(
        val repository: String,
        val tag: String,
    )
}
