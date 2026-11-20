/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.trace

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScopeBlocking
import hu.bme.mit.semantifyr.backend.transformation.WHOLE_DELAY_VARIABLE_NAME
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backends.spin.transformation.SPIN_STABLE_FLAG
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinVariableTransformer
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackendModule
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@InjectWithOxsts
class SpinInlinedOxstsWitnessTransformerTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var oxstsInjector: Injector

    private val inlinedOxsts: InlinedOxsts by lazy {
        models.parse(BackendTestModels.SATURATING_COUNTER_EXCEEDS_BOUND)
    }

    private fun transform(states: (String) -> List<Map<String, String>>): InlinedWitness {
        return withVerificationScopeBlocking {
            val injector = oxstsInjector.createChildInjector(SpinBackendModule())
            val counter = injector.getInstance(SpinVariableTransformer::class.java).nameOf(inlinedOxsts.variables.single())
            val trace = SpinTrace(
                states(counter).map {
                    SpinTraceState(it)
                },
            )
            injector.getInstance(SpinInlinedOxstsWitnessTransformer::class.java).transform(inlinedOxsts, trace, setOf(SPIN_STABLE_FLAG))
        }
    }

    @Test
    fun `the delay of a step is its elapsed time and no state value`() {
        val witness = transform { counter ->
            listOf(
                mapOf(
                    counter to "0",
                    SPIN_STABLE_FLAG to "1",
                ),
                mapOf(
                    SPIN_STABLE_FLAG to "0",
                    WHOLE_DELAY_VARIABLE_NAME to "2",
                ),
                mapOf(
                    counter to "1",
                    SPIN_STABLE_FLAG to "1",
                ),
            )
        }

        assertThat(witness.initializedState.elapsed).isEqualTo(Fraction.ZERO)
        assertThat(witness.transitionStates.single().elapsed).isEqualTo(Fraction.of(2))
        assertThat(
            witness.transitionStates.single().values.map {
                it.variable
            },
        ).containsExactlyElementsOf(inlinedOxsts.variables)
    }

    @Test
    fun `a value that is no value of its type fails the read-back`() {
        val failure = assertThrows<WitnessFailedException> {
            transform { counter ->
                listOf(
                    mapOf(
                        counter to "1.5",
                        SPIN_STABLE_FLAG to "1",
                    ),
                )
            }
        }

        assertThat(failure.message).contains("holds 1.5 for")
    }

    @Test
    fun `a name of no variable fails the read-back`() {
        val failure = assertThrows<WitnessFailedException> {
            transform {
                listOf(
                    mapOf(
                        "ghost" to "0",
                        SPIN_STABLE_FLAG to "1",
                    ),
                )
            }
        }

        assertThat(failure.message).contains("ghost, which names no variable of the model")
    }
}
