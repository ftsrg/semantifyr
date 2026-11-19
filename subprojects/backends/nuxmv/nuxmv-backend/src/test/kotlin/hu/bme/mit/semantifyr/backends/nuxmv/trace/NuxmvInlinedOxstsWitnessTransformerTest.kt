/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.trace

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScopeBlocking
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvVariableTransformer
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackendModule
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@InjectWithOxsts
class NuxmvInlinedOxstsWitnessTransformerTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var oxstsInjector: Injector

    private val inlinedOxsts: InlinedOxsts by lazy {
        models.parse(BackendTestModels.SATURATING_COUNTER_EXCEEDS_BOUND)
    }

    private fun transform(values: (String) -> Map<String, String>): InlinedWitness {
        return withVerificationScopeBlocking {
            val injector = oxstsInjector.createChildInjector(NuxmvBackendModule())
            val counter = injector.getInstance(NuxmvVariableTransformer::class.java).nameOf(inlinedOxsts.variables.single())
            val trace = NuxmvTrace(
                listOf(
                    NuxmvTraceState(values(counter), emptyMap()),
                ),
            )
            injector.getInstance(NuxmvInlinedOxstsWitnessTransformer::class.java).transform(inlinedOxsts, trace, emptySet())
        }
    }

    @Test
    fun `a value that is no value of its type fails the read-back`() {
        val failure = assertThrows<WitnessFailedException> {
            transform {
                mapOf(
                    it to "1.5",
                )
            }
        }

        assertThat(failure.message).contains("holds 1.5 for")
    }

    @Test
    fun `a name of no variable fails the read-back`() {
        val failure = assertThrows<WitnessFailedException> {
            transform {
                mapOf(
                    "ghost" to "0",
                )
            }
        }

        assertThat(failure.message).contains("ghost, which names no variable of the model")
    }
}
