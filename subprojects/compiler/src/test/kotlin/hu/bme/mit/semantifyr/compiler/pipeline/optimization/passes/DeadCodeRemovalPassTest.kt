/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConeOfInfluenceAnalysis
import org.junit.jupiter.api.Test

class DeadCodeRemovalPassTest : PassTestBase() {
    @Test
    fun `assignment to a variable not read by the property is removed`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var observed: int := 0
            var unread: int := 0
            init {
            }
            tran {
                observed := 1
                unread := 2
            }
            prop {
                AG (observed == 1)
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var observed: int := 0
            var unread: int := 0
            init {
            }
            tran {
                observed := 1
            }
            prop {
                AG (observed == 1)
            }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `two independent state machines - writes to the unobserved region are pruned`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var stateA: int := 0
            var stateB: int := 0
            var event: int := 0
            init {
            }
            tran {
                havoc(event)
                choice {
                    assume(event == 1)
                    stateA := 1
                } or {
                    assume(event == 2)
                    stateB := 1
                }
            }
            prop {
                AG (stateA != 1)
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var stateA: int := 0
            var stateB: int := 0
            var event: int := 0
            init {
            }
            tran {
                havoc(event)
                choice {
                    assume(event == 1)
                    stateA := 1
                } or {
                    assume(event == 2)
                }
            }
            prop {
                AG (stateA != 1)
            }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `havoc on an unread variable is dropped`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var observed: int := 0
            var unread: int := 0
            init {
            }
            tran {
                havoc(unread)
                observed := 5
            }
            prop {
                AG (observed != 5)
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var observed: int := 0
            var unread: int := 0
            init {
            }
            tran {
                observed := 5
            }
            prop {
                AG (observed != 5)
            }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `write to NonOptimizable variable is kept even when not in cone of influence`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            @NonOptimizable
            var tracer: bool := false
            init {
            }
            tran {
                tracer := true
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            @NonOptimizable
            var tracer: bool := false
            init {
            }
            tran {
                tracer := true
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `region marker local variable keeps its writes and activates the guarded branches`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var state: int := 0
            init {
            }
            tran {
                var anyRegionExecuted: bool := false
                choice {
                    assume(state == 0)
                    state := 1
                    anyRegionExecuted := true
                } or {
                    assume(state == 1)
                    state := 0
                    anyRegionExecuted := true
                }
                assume(anyRegionExecuted)
            }
            prop {
                AG (state != 1)
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var state: int := 0
            init {
            }
            tran {
                var anyRegionExecuted: bool := false
                choice {
                    assume(state == 0)
                    state := 1
                    anyRegionExecuted := true
                } or {
                    assume(state == 1)
                    state := 0
                    anyRegionExecuted := true
                }
                assume(anyRegionExecuted)
            }
            prop {
                AG (state != 1)
            }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }
}
