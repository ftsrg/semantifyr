/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path

@InjectWithOxsts
class SemanticRegressionVerificationTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val model by lazy {
            helper.semantifyrLoader.startContext()
                .loadModels(Path("test-models/semantic/"))
                .buildAndResolve()
        }

        @JvmStatic
        fun `Semantic regression verification cases should pass`(): List<Arguments> {
            return helper.collectVerificationCasesAsArguments(model)
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Semantic regression verification cases should pass`(verificationCase: VerificationCase) {
        helper.checkVerificationCase(model, verificationCase, Portfolios.ThetaFull)
    }

}
