/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

private class RecordingProgressContext : ProgressContext {
    val messages = mutableListOf<String>()
    var cancelled = false

    override fun checkIsCancelled() {
        if (cancelled) {
            throw CancellationException("cancelled")
        }
    }

    override fun reportProgress(message: String) {
        messages += message
    }
}

class ProgressContextTest {

    @Test
    fun `NoOp accepts reports and returns itself as child`() {
        ProgressContext.NoOp.reportProgress("ignored")
        ProgressContext.NoOp.checkIsCancelled()
        assertThat(ProgressContext.NoOp.child("any")).isSameAs(ProgressContext.NoOp)
    }

    @Test
    fun `child prefixes reports with the child name`() {
        val parent = RecordingProgressContext()
        val child = parent.child("ctx")

        child.reportProgress("hello")

        assertThat(parent.messages).containsExactly("ctx - hello")
    }

    @Test
    fun `nested children compose prefixes`() {
        val parent = RecordingProgressContext()
        val grandchild = parent.child("outer").child("inner")

        grandchild.reportProgress("hello")

        assertThat(parent.messages).containsExactly("outer - inner - hello")
    }

    @Test
    fun `child propagates cancellation to parent`() {
        val parent = RecordingProgressContext()
        val child = parent.child("ctx")

        parent.cancelled = true

        assertThatThrownBy {
            child.checkIsCancelled()
        }.isInstanceOf(CancellationException::class.java)
    }
}
