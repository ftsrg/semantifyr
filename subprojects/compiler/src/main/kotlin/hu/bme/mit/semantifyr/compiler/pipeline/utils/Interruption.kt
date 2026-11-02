/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

// a verifier runs a compilation interruptibly, so its timeout and cancellation stop the compilation at the next check
fun checkInterrupted() {
    if (Thread.interrupted()) {
        throw InterruptedException()
    }
}
