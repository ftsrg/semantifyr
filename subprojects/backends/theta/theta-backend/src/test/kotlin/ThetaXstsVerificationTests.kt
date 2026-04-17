/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification

import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.semantics.InjectWithOxstsSemantics
import hu.bme.mit.semantifyr.semantics.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.semantics.StandaloneOxstsSemanticsRuntimeModule
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.semantics.verification.VerificationCase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.streams.asStream

@Tag("verification")
@InjectWithOxstsSemantics
class ThetaXstsVerificationTests {

    companion object {

        private val helper = StandaloneOxstsSemanticsRuntimeModule.getInstance<SemantifyrVerifierTestHelper>()

        private val simpleModel: SemantifyrModelContext by lazy {
            helper.semantifyrLoader.startContext()
                .loadModel(Path("test-models/Simple/simple.oxsts"))
                .buildAndResolve()
        }

        @JvmStatic
        fun `Simple Model Verification Cases Should Pass`(): Stream<Arguments> =
            helper.collectVerificationCases(simpleModel).asStream()
    }

    @ParameterizedTest
    @MethodSource
    fun `Simple Model Verification Cases Should Pass`(verificationCase: VerificationCase) {
        helper.checkVerificationCase(simpleModel, verificationCase, Portfolios.ThetaFull)
    }

}
