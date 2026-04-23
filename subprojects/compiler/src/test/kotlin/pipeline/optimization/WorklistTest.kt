/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class WorklistTest {

    @Test
    fun `new worklist is empty`() {
        val worklist = Worklist<String>()
        assertThat(worklist.isNotEmpty()).isFalse
    }

    @Test
    fun `add then pop returns the added item and empties the queue`() {
        val worklist = Worklist<String>()
        worklist.add("a")
        assertThat(worklist.isNotEmpty()).isTrue
        assertThat(worklist.pop()).isEqualTo("a")
        assertThat(worklist.isNotEmpty()).isFalse
    }

    @Test
    fun `items are returned in FIFO order`() {
        val worklist = Worklist<String>()
        worklist.add("a")
        worklist.add("b")
        worklist.add("c")
        assertThat(worklist.pop()).isEqualTo("a")
        assertThat(worklist.pop()).isEqualTo("b")
        assertThat(worklist.pop()).isEqualTo("c")
    }

    @Test
    fun `duplicate adds while in queue are deduplicated`() {
        val worklist = Worklist<String>()
        worklist.add("a")
        worklist.add("a")
        worklist.add("a")
        assertThat(worklist.pop()).isEqualTo("a")
        assertThat(worklist.isNotEmpty()).isFalse
    }

    @Test
    fun `re-adding after pop enqueues again`() {
        val worklist = Worklist<String>()
        worklist.add("a")
        worklist.pop()
        worklist.add("a")
        assertThat(worklist.isNotEmpty()).isTrue
        assertThat(worklist.pop()).isEqualTo("a")
    }

    @Test
    fun `pop on empty worklist throws`() {
        val worklist = Worklist<String>()
        assertThatThrownBy {
            worklist.pop()
        }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `dedup does not disturb order of other items`() {
        val worklist = Worklist<String>()
        worklist.add("a")
        worklist.add("b")
        worklist.add("a")
        worklist.add("c")
        assertThat(worklist.pop()).isEqualTo("a")
        assertThat(worklist.pop()).isEqualTo("b")
        assertThat(worklist.pop()).isEqualTo("c")
    }
}
