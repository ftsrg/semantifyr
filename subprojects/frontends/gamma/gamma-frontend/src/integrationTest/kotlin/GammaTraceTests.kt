/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationCase
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ActiveStateAssertion
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FiredTransitionAssertion
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Step
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableValueAssertion
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaTestEngine
import hu.bme.mit.semantifyr.verifier.witness.WitnessStateStep
import org.assertj.core.api.Assertions.assertThat
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
                .isEqualTo(activeLeafStates(witnessStep))
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
        for (transition in step.asserts.filterIsInstance<FiredTransitionAssertion>()) {
            val source = instancePath(transition.from)
            val target = "${source.substringBeforeLast('.')}.${transition.to.name}"
            assertThat(previousStates)
                .describedAs("The source of the transition $source to ${transition.to.name} is active before $description")
                .anyMatch {
                    isWithin(it, source)
                }
            assertThat(currentStates)
                .describedAs("The target of the transition $source to ${transition.to.name} is active after $description")
                .anyMatch {
                    isWithin(it, target)
                }
        }
    }

    private fun isWithin(leafState: String, state: String): Boolean {
        return leafState == state || leafState.startsWith("$state.")
    }

    private fun assertedStates(step: Step): Set<String> {
        return step.asserts.filterIsInstance<ActiveStateAssertion>().map {
            instancePath(it.state)
        }.toSet()
    }

    private fun assertedVariables(step: Step): Map<String, String> {
        return step.asserts.filterIsInstance<VariableValueAssertion>().associate {
            instancePath(it.variable) to sourceText(it.value)
        }
    }

    private fun activeLeafStates(witnessStep: WitnessStateStep): Set<String> {
        val activeStates = witnessStep.values.filter {
            it.variable.endsWith(".activeState") && it.value != "nothing"
        }.map {
            it.value
        }
        return activeStates.filter { state ->
            activeStates.none {
                it.startsWith("$state.")
            }
        }.toSet()
    }

    private fun instancePath(path: Expression): String {
        return "root.${sourceText(path)}"
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
