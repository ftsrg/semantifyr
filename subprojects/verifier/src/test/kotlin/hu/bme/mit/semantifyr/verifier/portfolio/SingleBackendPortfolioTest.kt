/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.portfolio

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.fakeMetadata
import hu.bme.mit.semantifyr.verifier.noopGate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

class SingleBackendPortfolioTest {

    enum class Property {
        AG,
        EF,
    }

    companion object {
        @JvmStatic
        fun trustedResults(): List<Arguments> {
            return listOf(
                Arguments.of(SoundnessDirection.Exact, VerificationVerdict.Passed, Property.AG),
                Arguments.of(SoundnessDirection.Exact, VerificationVerdict.Failed, Property.AG),
                Arguments.of(SoundnessDirection.Exact, VerificationVerdict.Passed, Property.EF),
                Arguments.of(SoundnessDirection.Exact, VerificationVerdict.Failed, Property.EF),
                Arguments.of(SoundnessDirection.UnderApproximating, VerificationVerdict.Failed, Property.AG),
                Arguments.of(SoundnessDirection.UnderApproximating, VerificationVerdict.Passed, Property.EF),
                Arguments.of(SoundnessDirection.OverApproximating, VerificationVerdict.Passed, Property.AG),
                Arguments.of(SoundnessDirection.OverApproximating, VerificationVerdict.Failed, Property.EF),
            )
        }

        @JvmStatic
        fun untrustedResults(): List<Arguments> {
            return listOf(
                Arguments.of(SoundnessDirection.UnderApproximating, VerificationVerdict.Passed, Property.AG, "spin's Passed on an AG property is not trusted: the leg is under-approximating"),
                Arguments.of(SoundnessDirection.UnderApproximating, VerificationVerdict.Failed, Property.EF, "spin's Failed on an EF property is not trusted: the leg is under-approximating"),
                Arguments.of(SoundnessDirection.OverApproximating, VerificationVerdict.Failed, Property.AG, "spin's Failed on an AG property is not trusted: the leg is over-approximating"),
                Arguments.of(SoundnessDirection.OverApproximating, VerificationVerdict.Passed, Property.EF, "spin's Passed on an EF property is not trusted: the leg is over-approximating"),
            )
        }
    }

    private val parentInjector = mock<Injector>()

    @ParameterizedTest
    @MethodSource("trustedResults")
    suspend fun `a trusted verdict passes through with its witness and no note`(
        soundness: SoundnessDirection,
        verdict: VerificationVerdict,
        property: Property,
    ) {
        val witness = witness()

        val result = verify(soundness, verdict, property, witness)

        assertThat(result.verdict).isEqualTo(verdict)
        assertThat(result.isTrusted).isTrue
        assertThat(result.inlinedWitness).isSameAs(witness)
        assertThat(result.message).isNull()
    }

    @ParameterizedTest
    @MethodSource("untrustedResults")
    suspend fun `an untrusted verdict is Inconclusive with the note and without the witness`(
        soundness: SoundnessDirection,
        verdict: VerificationVerdict,
        property: Property,
        note: String,
    ) {
        val result = verify(soundness, verdict, property, witness())

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.isTrusted).isFalse
        assertThat(result.inlinedWitness).isNull()
        assertThat(result.message).isEqualTo(note)
        assertThat(result.soundness).isEqualTo(soundness)
        assertThat(result.metadata.backendId).isEqualTo("spin")
        assertThat(result.metrics.verificationDuration).isEqualTo(3.seconds)
    }

    private suspend fun verify(
        soundness: SoundnessDirection,
        verdict: VerificationVerdict,
        property: Property,
        witness: InlinedWitness,
    ): BackendVerificationResult {
        val request = request(property)
        val backend = FakeBackend("spin") {
            BackendVerificationResult(
                verdict = verdict,
                metadata = fakeMetadata("spin"),
                metrics = BackendMetrics(verificationDuration = 3.seconds),
                inlinedWitness = witness,
                soundness = soundness,
                isTrusted = soundness.trusts(verdict, request.inlinedOxsts.property.expression),
            )
        }
        val portfolio = SingleBackendPortfolio(backend, FakeConfig(soundness = soundness))
        return portfolio.verify(parentInjector, request, noopGate, ExecutionEnvironment.Empty, ProgressContext.Silent)
    }

    private fun request(property: Property): BackendVerificationRequest {
        val inlinedOxsts = OxstsFactory.createInlinedOxsts()
        inlinedOxsts.property = OxstsFactory.createPropertyDeclaration().also {
            it.expression = when (property) {
                Property.AG -> OxstsFactory.createAG()
                Property.EF -> OxstsFactory.createEF()
            }
        }
        return BackendVerificationRequest(
            inlinedOxsts = inlinedOxsts,
            artifactOutputPath = Paths.get(System.getProperty("java.io.tmpdir")),
        )
    }

    private fun witness(): InlinedWitness {
        return InlinedWitness(
            initializedState = WitnessState(emptyList()),
            transitionStates = emptyList(),
            nextStateMap = emptyMap(),
            inlinedOxsts = OxstsFactory.createInlinedOxsts(),
        )
    }
}
