/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PatternOptimizerTest {

    private val artifacts: CompilationArtifactManager = mock()

    @Test
    fun `empty pattern list reports no change and never commits`() {
        val optimizer = PatternOptimizer(emptyList(), CompilationPass.ExpressionSimplification, artifacts)
        val input = OxstsFactory.createLiteralBoolean(true)

        assertThat(optimizer.optimize(input)).isFalse
        verify(artifacts, never()).commitStep(any())
    }

    @Test
    fun `patterns that never match report no change and never commit`() {
        val pattern: OptimizationPattern = mock()
        whenever(pattern.tryApply(any(), any())).thenReturn(false)

        val optimizer = PatternOptimizer(listOf(pattern), CompilationPass.ExpressionSimplification, artifacts)
        val input = OxstsFactory.createLiteralBoolean(true)

        assertThat(optimizer.optimize(input)).isFalse
        verify(artifacts, never()).commitStep(any())
    }

    @Test
    fun `a matching pattern triggers commitStep and reports change`() {
        val pattern: OptimizationPattern = mock()
        // Fire once on the first element examined, then return false forever.
        var fired = false
        whenever(pattern.tryApply(any(), any())).thenAnswer {
            if (!fired) {
                fired = true
                true
            } else {
                false
            }
        }

        val optimizer = PatternOptimizer(listOf(pattern), CompilationPass.ExpressionSimplification, artifacts)
        val input = OxstsFactory.createLiteralBoolean(true)

        assertThat(optimizer.optimize(input)).isTrue
        verify(artifacts, times(1)).commitStep(eq(CompilationPass.ExpressionSimplification))
    }

    @Test
    fun `once a pattern matches later patterns are not tried on the same element`() {
        val first: OptimizationPattern = mock()
        val second: OptimizationPattern = mock()
        // Match first time only, for `first` pattern. After that, short-circuits.
        var fired = false
        whenever(first.tryApply(any(), any())).thenAnswer {
            if (!fired) {
                fired = true
                true
            } else {
                false
            }
        }
        whenever(second.tryApply(any(), any())).thenReturn(false)

        val optimizer = PatternOptimizer(
            listOf(first, second),
            CompilationPass.ExpressionSimplification,
            artifacts,
        )
        val input: EObject = OxstsFactory.createLiteralBoolean(true)

        optimizer.optimize(input)

        // `second` is still tried on subsequent worklist items (there may be none
        // here since a literal has no children), but for the element where `first`
        // matched, `second` must not have been invoked.
        verify(first, times(1)).tryApply(eq(input), any())
        verify(second, never()).tryApply(eq(input), any())
    }
}
