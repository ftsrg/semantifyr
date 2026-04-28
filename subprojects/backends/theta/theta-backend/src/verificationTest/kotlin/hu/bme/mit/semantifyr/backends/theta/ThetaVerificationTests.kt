/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backends.theta.execution.ShellBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.verification.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verification.SingleBackendPortfolio
import hu.bme.mit.semantifyr.verification.discovery.CaseFilter
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path

@InjectWithOxsts
class ThetaVerificationTests {
    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val simpleModel by lazy {
            helper.semantifyrLoader
                .startContext()
                .loadModel(Path("test-models/Simple/simple.oxsts"))
                .buildAndResolve()
        }

        @JvmStatic
        @BeforeAll
        fun assumeThetaAvailable() {
            Assumptions.assumeTrue(
                ShellBasedThetaXstsExecutor().isAvailable(),
                "theta-xsts-cli not on PATH - skipping Theta backend tests",
            )
        }

        @JvmStatic
        fun `Passing cases across all configs`(): List<Arguments> {
            val cases = helper.collectVerificationCases(simpleModel, CaseFilter.tagged("expect-pass"))
            return helper.productAsArguments(cases, ThetaConfig.Builtin) { it.id }
        }

        @JvmStatic
        fun `Failing cases across all configs`(): List<Arguments> {
            val cases = helper.collectVerificationCases(simpleModel, CaseFilter.tagged("expect-fail"))
            return helper.productAsArguments(cases, ThetaConfig.Builtin) { it.id }
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Passing cases across all configs`(verificationCase: VerificationCase, config: ThetaConfig) {
        helper.checkVerificationCase(
            simpleModel,
            verificationCase,
            SingleBackendPortfolio(ThetaBackend, config, config.id),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(ThetaVerificationTests::class.java).resolve(config.id),
            expectedVerdict = VerificationVerdict.Passed,
        )
    }

    @Disabled("test-models/Simple/simple.oxsts has no @Tag(\"expect-fail\") cases yet; re-enable once a failing fixture exists")
    @ParameterizedTest
    @MethodSource
    suspend fun `Failing cases across all configs`(verificationCase: VerificationCase, config: ThetaConfig) {
        helper.checkVerificationCase(
            simpleModel,
            verificationCase,
            SingleBackendPortfolio(ThetaBackend, config, config.id),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(ThetaVerificationTests::class.java).resolve(config.id),
            expectedVerdict = VerificationVerdict.Failed,
        )
    }
}
