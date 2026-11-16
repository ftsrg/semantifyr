/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.process

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val treeExitTimeout = 2.seconds

// a script whose child dies first runs its next command, so the root goes first and its descendants after
fun Process.destroyTree() {
    val handle = toHandle()
    val descendants = handle.descendants().toList()
    handle.destroy()
    descendants.forEach {
        try {
            it.destroy()
        } catch (_: IllegalStateException) {
            it.destroyForcibly()
        }
    }
    val tree = listOf(handle) + descendants
    if (!awaitExit(tree, treeExitTimeout)) {
        tree.filter {
            it.isAlive
        }.forEach {
            it.destroyForcibly()
        }
        awaitExit(tree, treeExitTimeout)
    }
}

private fun awaitExit(processes: List<ProcessHandle>, timeout: Duration): Boolean {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    while (
        processes.any {
            it.isAlive
        }
    ) {
        if (deadline.hasPassedNow()) {
            return false
        }
        Thread.sleep(10)
    }
    return true
}
