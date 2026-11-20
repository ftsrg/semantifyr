/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.internal

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.time.Clock

@InjectWithOxsts
class TrivialVerifierTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var trivialVerifier: TrivialVerifier

    @ParameterizedTest
    @CsvSource(
        "AG, false, true",
        "AG, true, false",
        "EF, true, true",
        "EF, false, false",
    )
    fun `a trivial verdict carries a witness only when it has a trace`(
        operator: String,
        body: Boolean,
        hasWitness: Boolean,
    ) {
        val inlinedOxsts = models.parse(
            """
                inlined oxsts of semantifyr::Anything
                init {
                }
                tran {
                }
                prop {
                    $operator $body
                }
            """,
        )

        val result = trivialVerifier.verifyTrivially(inlinedOxsts, Clock.System.now())

        assertThat(result.inlinedWitness != null).isEqualTo(hasWitness)
    }
}
