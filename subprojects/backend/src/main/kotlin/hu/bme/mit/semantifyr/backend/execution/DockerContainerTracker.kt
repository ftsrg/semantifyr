/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import com.github.dockerjava.api.DockerClient
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import java.util.concurrent.ConcurrentHashMap

internal object DockerContainerTracker {

    private val logger by loggerFactory()
    private val containers = ConcurrentHashMap<DockerClient, MutableSet<String>>()

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread({ killAll() }, "semantifyr-docker-killer"),
        )
    }

    inline fun <T> tracking(
        client: DockerClient,
        containerId: String,
        block: () -> T,
    ): T {
        try {
            track(client, containerId)
            return block()
        } finally {
            untrack(client, containerId)
        }
    }

    fun track(
        client: DockerClient,
        containerId: String,
    ) {
        containers.computeIfAbsent(client) {
            ConcurrentHashMap.newKeySet()
        }.add(containerId)
    }

    fun untrack(
        client: DockerClient,
        containerId: String,
    ) {
        containers[client]?.remove(containerId)
    }

    private fun killAll() {
        val snapshot = containers.mapValues {
            it.value.toList()
        }
        val total = snapshot.values.sumOf { it.size }
        if (total == 0) {
            return
        }

        logger.info { "Shutdown: stopping and removing $total still-running container(s)" }

        for ((client, ids) in snapshot) {
            for (id in ids) {
                try {
                    client.stopContainerCmd(id).exec()
                } catch (throwable: Throwable) {
                    logger.warn(throwable) { "Error stopping container." }
                    // Container may have already exited.
                }
                try {
                    client.removeContainerCmd(id).withForce(true).exec()
                } catch (throwable: Throwable) {
                    logger.warn(throwable) { "Error removing container." }
                    // Best-effort.
                }
            }
        }
    }
}
