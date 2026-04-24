/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import com.github.dockerjava.api.DockerClient
import hu.bme.mit.semantifyr.logging.loggerFactory
import java.util.concurrent.ConcurrentHashMap

internal object DockerContainerTracker {

    private val logger by loggerFactory()
    private val containers = ConcurrentHashMap<DockerClient, MutableSet<String>>()

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread({ killAll() }, "semantifyr-docker-killer"),
        )
    }

    inline fun <T> tracking(client: DockerClient, containerId: String, block: () -> T): T {
        track(client, containerId)
        try {
            return block()
        } finally {
            untrack(client, containerId)
        }
    }

    fun track(client: DockerClient, containerId: String) {
        containers.computeIfAbsent(client) {
            ConcurrentHashMap.newKeySet()
        }.add(containerId)
    }

    fun untrack(client: DockerClient, containerId: String) {
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

        logger.info("Shutdown: stopping and removing $total still-running container(s)")
        for ((client, ids) in snapshot) {
            for (id in ids) {
                try {
                    client.stopContainerCmd(id).exec()
                } catch (_: Throwable) {
                    // Container may already be exited — keep going to the remove step.
                }
                try {
                    client.removeContainerCmd(id).withForce(true).exec()
                } catch (_: Throwable) {
                    // Best-effort; a dangling container is less harmful than blocking shutdown.
                }
            }
        }
    }
}
