/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.clock

import hu.bme.mit.semantifyr.compiler.pipeline.flattening.FlatteningTestBase
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClockBoundCheckerTest : FlatteningTestBase() {

    @Test
    fun `a bound that a run-time argument binds is refused on the inlined model`() {
        val exception = assertThrows<IllegalStateException> {
            flatten("RunTimeBound", MODEL)
        }

        assertThat(exception.message).contains("known only at run time")
    }

    @Test
    fun `a bound that a constant argument or a feature binds compiles`() {
        flatten("CompileTimeBound", MODEL)
    }

    @Test
    fun `a run-time bound forwarded through a second call is refused`() {
        val exception = assertThrows<IllegalStateException> {
            flatten("RelayedRunTimeBound", MODEL)
        }

        assertThat(exception.message).contains("known only at run time")
    }

    @Test
    fun `a constant bound forwarded through a second call compiles`() {
        flatten("RelayedCompileTimeBound", MODEL)
    }

    @Test
    fun `a clock that the verified property reads through a property it calls is refused`() {
        val exception = assertThrows<IllegalStateException> {
            flatten("PropertyReadsClock", MODEL)
        }

        assertThat(exception.message).contains("The verified property reads a clock")
    }

    @Test
    fun `a property that reads a clock compiles in an assumption`() {
        flatten("AssumedProperty", MODEL)
    }

    companion object {
        private val MODEL = testModel(ClockBoundCheckerTest::class.java, "model.semantifyr")
    }

}
