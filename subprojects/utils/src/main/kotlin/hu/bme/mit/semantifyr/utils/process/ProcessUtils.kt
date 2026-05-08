/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.process

fun Process.destroyTree() {
    val handle = toHandle()
    handle.descendants().forEach {
        try {
            it.destroy()
        } catch (_: IllegalStateException) {
            it.destroyForcibly()
        }
    }
    handle.destroy()
}
