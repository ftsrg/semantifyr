/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import hu.bme.mit.semantifyr.logging.loggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks live [Process] handles launched by [BaseShellExecutor] and destroys them — along with
 * every descendant in the process tree — when the JVM shuts down. Without this hook, a hard kill
 * of the Semantifyr JVM (Ctrl+C, SIGTERM, an IDE stop button) leaves the spawned `verifyta` /
 * `nuXmv` / `spin` / `theta-xsts-cli` binaries running and holding their file descriptors.
 *
 * Processes register themselves on start and unregister on clean termination, so the shutdown
 * hook only sees the subset that was still running at JVM exit time.
 */
internal object ShellProcessTracker {

    private val logger by loggerFactory()
    private val processes = ConcurrentHashMap.newKeySet<Process>()

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread({ killAll() }, "semantifyr-shell-killer"),
        )
    }

    fun track(process: Process) {
        processes += process
    }

    fun untrack(process: Process) {
        processes -= process
    }

    private fun killAll() {
        val snapshot = processes.toList()
        if (snapshot.isEmpty()) return
        logger.info("Shutdown: destroying ${snapshot.size} still-running child process tree(s)")
        for (process in snapshot) {
            try {
                val handle = process.toHandle()
                handle.descendants().forEach { it.destroyForcibly() }
                handle.destroyForcibly()
            } catch (_: Throwable) {
                // Best-effort: do not let a stuck process block other cleanup.
            }
        }
    }
}
