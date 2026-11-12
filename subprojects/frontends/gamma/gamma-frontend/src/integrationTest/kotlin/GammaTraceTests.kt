/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationCase
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComparisonOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FiredTransitionExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaUtils
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InstanceElementExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NamedElement
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StatechartDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Step
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.serialization.markName
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaTestEngine
import hu.bme.mit.semantifyr.verifier.witness.WitnessStateStep
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.nodemodel.util.NodeModelUtils
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.time.Duration.Companion.minutes

class GammaTraceTests {

    companion object {
        private val helper = GammaFrontendTestHelper()
        private val artifactRoot = GammaFrontendTestHelper.testArtifactRoot(GammaTraceTests::class.java)

        @JvmStatic
        fun `Generated Traces Agree With The Witness`(): Stream<Arguments> {
            return helper.witnessCasesOnEveryEngine()
        }
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource
    suspend fun `Generated Traces Agree With The Witness`(gammaVerificationCase: GammaVerificationCase, engine: GammaTestEngine) {
        val witness = helper.checkVerificationCaseOn(engine, gammaVerificationCase, artifactRoot, timeout = 5.minutes).witness

        assertThat(witness).describedAs("An EF case that passes has a witness").isNotNull()
        val gammaTrace = helper.readGammaTrace(gammaVerificationCase, witness!!)
        val witnessState = witness.trace.witnessState
        val witnessSteps = listOf(witnessState.initializedStep) + witnessState.steps
        assertThat(gammaTrace.steps).hasSameSizeAs(witnessSteps)

        for ((index, step) in gammaTrace.steps.withIndex()) {
            if (index > 0) {
                assertFiredTransitionsMoveTheState(step, assertedStates(gammaTrace.steps[index - 1]), "step $index of ${gammaVerificationCase.qualifiedName}")
            }
            val witnessStep = witnessSteps[index] ?: continue
            assertThat(assertedStates(step))
                .describedAs("Active states in step $index of ${gammaVerificationCase.qualifiedName}")
                .isEqualTo(activeStates(witnessStep))
            for ((variable, value) in assertedVariables(step)) {
                val witnessValues = witnessStep.values.filter {
                    it.variable.substringBeforeLast('.') == variable
                }.map {
                    withoutWhitespace(it.value)
                }
                assertThat(witnessValues)
                    .describedAs("Value of $variable in step $index of ${gammaVerificationCase.qualifiedName}")
                    .containsExactly(value)
            }
        }
    }

    private fun assertFiredTransitionsMoveTheState(
        step: Step,
        previousStates: Set<String>,
        description: String,
    ) {
        val currentStates = assertedStates(step)
        for (firedTransition in step.asserts.filterIsInstance<FiredTransitionExpression>()) {
            val transition = firedTransition.transition
            val source = elementPath(firedTransition, transition?.from ?: firedTransition.from)
            val target = elementPath(firedTransition, transition?.to ?: firedTransition.to)
            assertThat(previousStates)
                .describedAs("The source of the transition $source to $target is active before $description")
                .contains(source)
            assertThat(currentStates)
                .describedAs("The target of the transition $source to $target is active after $description")
                .contains(target)
        }
    }

    private fun assertedStates(step: Step): Set<String> {
        return step.asserts.filterIsInstance<StateReferenceExpression>().map {
            elementPath(it, it.state)
        }.toSet()
    }

    private fun assertedVariables(step: Step): Map<String, String> {
        return step.asserts.filterIsInstance<ComparisonOperator>().filter {
            it.left is VariableReferenceExpression
        }.associate {
            val reference = it.left as VariableReferenceExpression
            elementPath(reference, reference.variable) to sourceText(it.right)
        }
    }

    private fun activeStates(witnessStep: WitnessStateStep): Set<String> {
        return witnessStep.values.filter {
            it.variable.endsWith(".activeState") && it.value != "nothing"
        }.map {
            it.value
        }.toSet()
    }

    private fun elementPath(expression: InstanceElementExpression, element: EObject): String {
        val instances = GammaUtils.getInstancePath(expression).map {
            markName(it.name)
        }
        val containment = mutableListOf<String>()
        var current = element
        while (current !is StatechartDeclaration) {
            containment.add(0, markName((current as NamedElement).name))
            current = current.eContainer()
        }
        return (listOf("root", "component") + instances + containment).joinToString(".")
    }

    private fun sourceText(expression: Expression): String {
        return withoutWhitespace(NodeModelUtils.getTokenText(NodeModelUtils.getNode(expression)))
    }

    private fun withoutWhitespace(text: String): String {
        return text.filterNot {
            it.isWhitespace()
        }
    }
}
