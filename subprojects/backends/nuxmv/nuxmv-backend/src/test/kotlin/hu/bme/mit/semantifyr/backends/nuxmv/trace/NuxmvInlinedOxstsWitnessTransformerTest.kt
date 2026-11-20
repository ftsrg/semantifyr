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
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvVariableTransformer
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackendModule
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

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

    private val clockModel: InlinedOxsts by lazy {
        models.parse(
            """
                inlined oxsts of semantifyr::Anything
                var elapsed : clock := 0
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )
    }

    private fun readClock(raw: String): Fraction {
        val witness = withVerificationScopeBlocking {
            val injector = oxstsInjector.createChildInjector(NuxmvBackendModule())
            val elapsed = injector.getInstance(NuxmvVariableTransformer::class.java).nameOf(clockModel.variables.single())
            val trace = NuxmvTrace(
                listOf(
                    NuxmvTraceState(mapOf(elapsed to raw), emptyMap()),
                ),
            )
            injector.getInstance(NuxmvInlinedOxstsWitnessTransformer::class.java).transform(clockModel, trace, emptySet())
        }
        return witness.initializedState.clockValues.single().value
    }

    @ParameterizedTest
    @CsvSource(
        quoteCharacter = '"',
        value = [
            "0.0, 0, 1",
            "1234567890.0, 1234567890, 1",
            "-1.0, -1, 1",
            "f'17/6, 17, 6",
            "-f'3/2, -3, 2",
        ],
    )
    fun `a real in a form nuXmv prints is read exactly`(
        raw: String,
        numerator: Long,
        denominator: Long,
    ) {
        assertThat(readClock(raw)).isEqualTo(Fraction.of(numerator, denominator))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "1",
            "1.5",
            "+1.0",
            "1E3",
            ".5",
            "1/2",
            "f'1/0",
            "f'-1/2",
        ],
    )
    fun `a real in a form nuXmv does not print fails the read-back`(raw: String) {
        val failure = assertThrows<WitnessFailedException> {
            readClock(raw)
        }

        assertThat(failure.message).contains("holds $raw for")
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
