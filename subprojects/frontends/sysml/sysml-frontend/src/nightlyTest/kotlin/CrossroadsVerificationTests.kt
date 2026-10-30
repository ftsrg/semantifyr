/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.VerificationCase
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class CrossroadsVerificationTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(CrossroadsVerificationTests::class.java)

        private val CROSSROADS = SysMLv2FrontendTestHelper.modelPath("crossroads.sysml")

        @JvmStatic
        fun `Crossroads Model Verification Cases Should Pass`(): Stream<Arguments> {
            return helper.discoverAsArguments(CROSSROADS, Portfolios.SmartFull, artifactRoot)
        }
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Crossroads Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        val frontend = helper.buildFrontend(CROSSROADS, Portfolios.SmartFull, artifactRoot, variant)
        helper.checkConformance(
            frontend = frontend,
            case = verificationCase,
            validationPortfolio = Portfolios.AllAgree,
        )
    }
}
