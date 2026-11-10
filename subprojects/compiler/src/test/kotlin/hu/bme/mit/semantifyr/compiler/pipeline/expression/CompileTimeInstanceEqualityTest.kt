/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.expression

import hu.bme.mit.semantifyr.compiler.pipeline.inlining.InliningTestBase
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.BooleanEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CompileTimeInstanceEqualityTest : InliningTestBase() {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        "items == pair, true",
        "items != pair, false",
        "items == first, false",
        "first == items, false",
        "items != first, true",
        "absent == first, false",
        "absent != first, true",
        "absent == nothing, true",
        "nothing == absent, true",
        "first == nothing, false",
        "nothing != first, true",
        "nothing == nothing, true",
    )
    fun `instance sets compare as sets`(
        comparison: String,
        expected: Boolean,
    ) {
        val source = """
            package expression::tests
            class Item {
            }
            @VerificationCase
            class Host {
                contains items: Item[0..*]
                contains first: Item[1] subsets items
                contains second: Item[1] subsets items
                contains absent: Item[0..1]
                refers pair: Item[0..*]
                refers secondOfPair: Item[1] subsets pair = second
                refers firstOfPair: Item[1] subsets pair = first
                refers comparison: bool = $comparison
                prop {
                    return AG true
                }
            }
        """

        val evaluation = prepare("Host", source) {
            val hostInstance = it.rootInstance.instancesAt(it.inlinedOxsts.rootFeature).single()
            val comparisonFeature = it.compilationInjector
                .getInstance(RedefinitionAwareReferenceResolver::class.java)
                .resolve(hostInstance.domain, "comparison") as FeatureDeclaration
            it.compilationInjector
                .getInstance(CompileTimeExpressionEvaluatorProvider::class.java)
                .evaluate(hostInstance, comparisonFeature.expression)
        }

        assertThat(evaluation).isEqualTo(BooleanEvaluation(expected))
    }
}
