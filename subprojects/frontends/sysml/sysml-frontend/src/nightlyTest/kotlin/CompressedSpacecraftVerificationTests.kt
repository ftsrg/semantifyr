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

class CompressedSpacecraftVerificationTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(CompressedSpacecraftVerificationTests::class.java)

        private val COMPRESSED_SPACECRAFT = SysMLv2FrontendTestHelper.modelPath("compressedspacecraft.sysml")

        @JvmStatic
        fun `Compressed Spacecraft Model Verification Cases Should Pass`(): Stream<Arguments> {
            return helper.discoverAsArguments(COMPRESSED_SPACECRAFT, Portfolios.SmartFull, artifactRoot)
        }
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Compressed Spacecraft Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        val frontend = helper.buildFrontend(COMPRESSED_SPACECRAFT, Portfolios.SmartFull, artifactRoot, variant)
        helper.checkConformance(
            frontend = frontend,
            case = verificationCase,
            validationPortfolio = Portfolios.AllAgree,
        )
    }
}
