/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

interface ProgressContext {

    fun checkIsCancelled()

    fun reportProgress(message: String)

    fun child(name: String): ProgressContext {
        return PrefixedProgressContext(name, this)
    }

    object NoOp : ProgressContext {
        override fun checkIsCancelled() {
            // NO-OP
        }

        override fun reportProgress(message: String) {
            // NO-OP
        }

        override fun child(name: String): ProgressContext {
            return this
        }
    }
}

private class PrefixedProgressContext(
    private val prefix: String,
    private val parent: ProgressContext,
) : ProgressContext {
    override fun checkIsCancelled() {
        parent.checkIsCancelled()
    }

    override fun reportProgress(message: String) {
        parent.reportProgress("$prefix - $message")
    }
}
