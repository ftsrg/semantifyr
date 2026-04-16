/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.wrapper.utils

fun Process.destroyTree() {
    val handle = toHandle()
    val descendants = handle.descendants()

    for (descendant in descendants) {
        descendant.destroy()
    }

    handle.destroy()
}

fun Process.destroyTreeForcibly() {
    val handle = toHandle()
    val descendants = handle.descendants()

    for (descendant in descendants) {
        descendant.destroyForcibly()
    }

    handle.destroyForcibly()
}
