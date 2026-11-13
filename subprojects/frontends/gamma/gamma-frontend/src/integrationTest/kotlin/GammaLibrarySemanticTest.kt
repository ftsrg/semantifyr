/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path

class GammaLibrarySemanticTest {

    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val context by lazy {
            helper.semantifyrLoader.startContext()
                .loadLibraries(Path("..", "models", "libraries", "default"))
                .loadModels(Path("..", "models", "etc", "semantics"))
                .buildAndResolve()
        }

        @JvmStatic
        fun `Gamma Library Cases Reach Their Expected Verdict`(): List<Arguments> {
            return helper.collectVerificationCasesAsArguments(context)
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Gamma Library Cases Reach Their Expected Verdict`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            context,
            verificationCase,
            verificationPortfolio = Portfolios.SmartFull,
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(GammaLibrarySemanticTest::class.java),
        )
    }
}
