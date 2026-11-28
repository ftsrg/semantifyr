/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConeOfInfluenceAnalysis
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
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
        source = testModel(javaClass, "independent_state_machines.semantifyr"),
        expectedSource = testModel(javaClass, "independent_state_machines_expected.semantifyr"),
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
        source = testModel(javaClass, "region_marker_local.semantifyr"),
        expectedSource = testModel(javaClass, "region_marker_local_expected.semantifyr"),
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }
}
