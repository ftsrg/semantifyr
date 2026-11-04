/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import org.junit.jupiter.api.Test

class VariableLivenessPassTest : PassTestBase() {
    @Test
    fun `unread variable and its assignments are removed`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var observed : int := 0
            var unread : int := 0
            init { }
            tran {
                observed := 1
                unread := 2
            }
            prop { AG (observed == 1) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var observed : int := 0
            init { }
            tran { observed := 1 }
            prop { AG (observed == 1) }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `initializer-only variable is substituted into its reads`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var constant : int := 7
            init { }
            tran { }
            prop { AG (constant == 7) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            init { }
            tran { }
            prop { AG (7 == 7) }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `single unused variable is fully eliminated, yielding a zero-variable model`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var dummy : int := 0
            init { }
            tran { }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            init { }
            tran { }
            prop { AG true }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `variable with both havoc and assignment is left alone when read`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran {
                havoc(value)
                value := 1
            }
            prop { AG (value == 1) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran {
                havoc(value)
                value := 1
            }
            prop { AG (value == 1) }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `unread NonOptimizable variable survives elimination`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            @NonOptimizable
            var tracer : bool := false
            init { }
            tran { tracer := true }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            @NonOptimizable
            var tracer : bool := false
            init { }
            tran { tracer := true }
            prop { AG true }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `a local's initializer is not moved past a later write of its source`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source : int := 0
            var seen : int := 0
            init { }
            tran {
                var snapshot : int := source
                source := source + 1
                seen := snapshot
            }
            prop { AG (seen != source) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source : int := 0
            var seen : int := 0
            init { }
            tran {
                var snapshot : int := source
                source := source + 1
                seen := snapshot
            }
            prop { AG (seen != source) }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `a local's initializer is not moved past a write of its source on one branch`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source : int := 0
            var seen : int := 0
            init { }
            tran {
                var snapshot : int := source
                choice { source := 1 } or { }
                seen := snapshot
            }
            prop { AG (seen != source) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source : int := 0
            var seen : int := 0
            init { }
            tran {
                var snapshot : int := source
                choice { source := 1 } or { }
                seen := snapshot
            }
            prop { AG (seen != source) }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `a local's initializer is not moved into a loop whose body writes its source`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source : int := 0
            var seen : int := 0
            init { }
            tran {
                var snapshot : int := source
                for (index in 0..2) {
                    seen := snapshot
                    source := source + index
                }
            }
            prop { AG (seen != source) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source : int := 0
            var seen : int := 0
            init { }
            tran {
                var snapshot : int := source
                for (index in 0..2) {
                    seen := snapshot
                    source := source + index
                }
            }
            prop { AG (seen != source) }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `a local's initializer is substituted when its source is written only after the reads`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source : int := 0
            var seen : int := 0
            init { }
            tran {
                var snapshot : int := source
                seen := snapshot
                source := source + 1
            }
            prop { AG (seen != source) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source : int := 0
            var seen : int := 0
            init { }
            tran {
                seen := source
                source := source + 1
            }
            prop { AG (seen != source) }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `an unread runtime loop variable is kept`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var counter : int := 0
            init { }
            tran {
                for (index in 0..2) {
                    counter := counter + 1
                }
            }
            prop { AG (counter <= 3) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var counter : int := 0
            init { }
            tran {
                for (index in 0..2) {
                    counter := counter + 1
                }
            }
            prop { AG (counter <= 3) }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `an unread runtime loop variable is kept when the loop body is empty`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var counter : int := 0
            init { }
            tran {
                for (index in 0..2) { }
                counter := 1
            }
            prop { AG (counter <= 1) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var counter : int := 0
            init { }
            tran {
                for (index in 0..2) { }
                counter := 1
            }
            prop { AG (counter <= 1) }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }
}
