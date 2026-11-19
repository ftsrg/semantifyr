/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SoundnessDirectionTest {

    private val invariant = OxstsFactory.createAG()
    private val reachability = OxstsFactory.createEF()

    @Test
    fun `an exact engine is trusted in both directions`() {
        assertThat(SoundnessDirection.Exact.trusts(VerificationVerdict.Passed, invariant)).isTrue
        assertThat(SoundnessDirection.Exact.trusts(VerificationVerdict.Failed, invariant)).isTrue
        assertThat(SoundnessDirection.Exact.trusts(VerificationVerdict.Passed, reachability)).isTrue
        assertThat(SoundnessDirection.Exact.trusts(VerificationVerdict.Failed, reachability)).isTrue
    }

    @Test
    fun `an under-approximating engine is trusted only when it found a trace`() {
        assertThat(SoundnessDirection.UnderApproximating.trusts(VerificationVerdict.Failed, invariant)).isTrue
        assertThat(SoundnessDirection.UnderApproximating.trusts(VerificationVerdict.Passed, invariant)).isFalse
        assertThat(SoundnessDirection.UnderApproximating.trusts(VerificationVerdict.Passed, reachability)).isTrue
        assertThat(SoundnessDirection.UnderApproximating.trusts(VerificationVerdict.Failed, reachability)).isFalse
    }

    @Test
    fun `an over-approximating engine is trusted only when it explored everything`() {
        assertThat(SoundnessDirection.OverApproximating.trusts(VerificationVerdict.Passed, invariant)).isTrue
        assertThat(SoundnessDirection.OverApproximating.trusts(VerificationVerdict.Failed, invariant)).isFalse
        assertThat(SoundnessDirection.OverApproximating.trusts(VerificationVerdict.Failed, reachability)).isTrue
        assertThat(SoundnessDirection.OverApproximating.trusts(VerificationVerdict.Passed, reachability)).isFalse
    }

    @Test
    fun `an over-approximating and an under-approximating engine never trust the same verdict`() {
        val decisive = VerificationVerdict.entries.filter {
            it.isDecisive
        }
        for (property in listOf(invariant, reachability)) {
            for (verdict in decisive) {
                val over = SoundnessDirection.OverApproximating.trusts(verdict, property)
                val under = SoundnessDirection.UnderApproximating.trusts(verdict, property)
                assertThat(over != under).describedAs("$verdict on ${property::class.simpleName}").isTrue
            }
        }
    }

    @Test
    fun `a lowering decides the direction of an exact run and keeps that of a run in its own direction`() {
        for (direction in SoundnessDirection.entries) {
            assertThat(direction.throughLowering(SoundnessDirection.Exact)).isEqualTo(direction)
            assertThat(SoundnessDirection.Exact.throughLowering(direction)).isEqualTo(direction)
            assertThat(direction.throughLowering(direction)).isEqualTo(direction)
        }
    }

    @Test
    fun `a lowering against the direction of the run is refused`() {
        assertThrows<IllegalStateException> {
            SoundnessDirection.OverApproximating.throughLowering(SoundnessDirection.UnderApproximating)
        }
        assertThrows<IllegalStateException> {
            SoundnessDirection.UnderApproximating.throughLowering(SoundnessDirection.OverApproximating)
        }
    }

    @Test
    fun `no direction trusts an undecided verdict`() {
        val undecided = VerificationVerdict.entries.filter {
            !it.isDecisive
        }
        for (direction in SoundnessDirection.entries) {
            for (verdict in undecided) {
                assertThat(direction.trusts(verdict, invariant)).describedAs("$direction trusts $verdict").isFalse
            }
        }
    }
}
