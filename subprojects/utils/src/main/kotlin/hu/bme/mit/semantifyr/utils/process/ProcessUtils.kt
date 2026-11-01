/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.process

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
}
