/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.utils.process.destroyTree
import java.util.concurrent.ConcurrentHashMap

internal object ShellProcessTracker {

    private val logger by loggerFactory()
    private val processes = ConcurrentHashMap.newKeySet<Process>()

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread({ killAll() }, "semantifyr-shell-killer"),
        )
    }

    inline fun tracking(process: Process, block: () -> Unit) {
        try {
            track(process)
            block()
        } finally {
            untrack(process)
        }
    }

    fun track(process: Process) {
        processes += process
    }

    fun untrack(process: Process) {
        processes -= process
    }

    private fun killAll() {
        val snapshot = processes.toList()
        if (snapshot.isEmpty()) {
            return
        }

        logger.info("Shutdown: destroying ${snapshot.size} still-running child process tree(s)")

        for (process in snapshot) {
            process.destroyTree()
        }
    }
}
