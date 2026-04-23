/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.PackageExpanderTestBase
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PatternOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class FeatureTypedNothingComparisonPatternTest : PackageExpanderTestBase() {

    @Test
    fun `feature-typed slot with lower bound 1 equal-to-nothing folds to false`() {
        val folded = runPattern(
            """
                package optimization::tests::feature_nothing_eq
                class Leaf { }
                @VerificationCase
                class Host {
                    contains leaf : Leaf[1]
                    var slot : leaf[1] := nothing
                    prop { return EF slot == nothing }
                }
            """,
            className = "Host",
        )

        assertThat(folded.propertyBoolLiterals()).contains(false)
        assertThat(folded.propertyHasNothingComparison()).isFalse
    }

    @Test
    fun `feature-typed slot with lower bound 1 not-equal-to-nothing folds to true`() {
        val folded = runPattern(
            """
                package optimization::tests::feature_nothing_neq
                class Leaf { }
                @VerificationCase
                class Host {
                    contains leaf : Leaf[1]
                    var slot : leaf[1] := nothing
                    prop { return EF slot != nothing }
                }
            """,
            className = "Host",
        )

        assertThat(folded.propertyBoolLiterals()).contains(true)
        assertThat(folded.propertyHasNothingComparison()).isFalse
    }

    @Test
    fun `feature-typed slot with lower bound 0 is not folded`() {
        val beforeAndAfter = runPattern(
            """
                package optimization::tests::feature_nothing_optional
                class Leaf { }
                @VerificationCase
                class Host {
                    contains leaf : Leaf[0..1]
                    var slot : leaf[0..1] := nothing
                    prop { return EF slot == nothing }
                }
            """,
            className = "Host",
        )

        assertThat(beforeAndAfter.propertyHasNothingComparison())
            .`as`("optional feature should leave the nothing-comparison intact")
            .isTrue
    }

    @Test
    fun `nothing on the left operand folds the same way as on the right`() {
        val folded = runPattern(
            """
                package optimization::tests::feature_nothing_left
                class Leaf { }
                @VerificationCase
                class Host {
                    contains leaf : Leaf[1]
                    var slot : leaf[1] := nothing
                    prop { return EF nothing == slot }
                }
            """,
            className = "Host",
        )

        assertThat(folded.propertyBoolLiterals()).contains(false)
        assertThat(folded.propertyHasNothingComparison()).isFalse
    }

    private fun runPattern(source: String, className: String): InlinedOxsts {
        val prepared = prepare(className, source)
        val inlined = inlineAll(prepared).inlinedOxsts

        val pattern = FeatureTypedNothingComparisonPattern(
            prepared.compilationInjector.getInstance(MetaConstantExpressionEvaluatorProvider::class.java),
            prepared.compilationInjector.getInstance(MultiplicityRangeEvaluator::class.java),
        )
        val optimizer = PatternOptimizer(
            patterns = listOf(pattern),
            pass = CompilationPass.ExpressionSimplification,
            artifactManager = mock<CompilationArtifactManager>(),
        )
        optimizer.optimize(inlined)
        return inlined
    }

    private fun InlinedOxsts.propertyBoolLiterals(): List<Boolean> {
        val property = eAllOfType<PropertyDeclaration>().first()
        return property.expression.eAllOfType<LiteralBoolean>().map { it.isValue }.toList()
    }

    private fun InlinedOxsts.propertyHasNothingComparison(): Boolean {
        val property = eAllOfType<PropertyDeclaration>().first()
        return property.expression.eAllOfType<ComparisonOperator>().any {
            it.left is LiteralNothing || it.right is LiteralNothing
        }
    }
}
