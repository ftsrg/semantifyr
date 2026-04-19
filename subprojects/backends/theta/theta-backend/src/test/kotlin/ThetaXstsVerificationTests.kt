/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification

import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verification.SemantifyrVerifierTestHelper
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path

@InjectWithOxsts
class ThetaXstsVerificationTests {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val simpleModel by lazy {
            helper.semantifyrLoader.startContext()
                .loadModel(Path("test-models/Simple/simple.oxsts"))
                .buildAndResolve()
        }

        @JvmStatic
        fun `Simple Model Verification Cases Should Pass`(): List<Arguments> {
            return helper.collectVerificationCasesAsArguments(simpleModel)
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Simple Model Verification Cases Should Pass`(verificationCase: VerificationCase) {
        helper.checkVerificationCase(simpleModel, verificationCase, Portfolios.ThetaFull)
    }

}
