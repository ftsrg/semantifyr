/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import com.github.dockerjava.api.DockerClient
import hu.bme.mit.semantifyr.logging.loggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks live Docker containers launched by [BaseDockerExecutor] and stops + removes them when
 * the JVM shuts down. The normal `finally`-block in `runContainerWithCleanup` already handles the
 * happy path; this hook catches the scenarios where the JVM exits without running finally blocks
 * (hard kill, uncaught top-level error, IDE abort). Without it, containers orphaned by a crashed
 * Semantifyr run keep running on the Docker daemon.
 *
 * Containers register after `createContainerCmd(...).exec()` succeeds and unregister once they've
 * been cleanly removed, so the hook only sees containers that were still live at shutdown.
 */
internal object DockerContainerTracker {

    private val logger by loggerFactory()
    private val containers = ConcurrentHashMap<DockerClient, MutableSet<String>>()

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread({ killAll() }, "semantifyr-docker-killer"),
        )
    }

    fun track(client: DockerClient, containerId: String) {
        containers.computeIfAbsent(client) { ConcurrentHashMap.newKeySet() }.add(containerId)
    }

    fun untrack(client: DockerClient, containerId: String) {
        containers[client]?.remove(containerId)
    }

    private fun killAll() {
        val snapshot = containers.mapValues { it.value.toList() }
        val total = snapshot.values.sumOf { it.size }
        if (total == 0) return
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
