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
import java.nio.file.Path
import java.util.stream.Stream

class SysMLConformanceTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLConformanceTests::class.java)

        private val SEMANTICS_TEST = SysMLv2FrontendTestHelper.modelPath("semanticstest.sysml")
        private val TOPDOWN = SysMLv2FrontendTestHelper.modelPath("topdowntransition.sysml")

        private fun argumentsFor(
            sourcePath: Path,
            variants: List<SysMLv2Variant> = listOf(SysMLv2Variant.Default),
        ): Stream<Arguments> {
            return helper.discoverAsArguments(sourcePath, Portfolios.SmartFull, artifactRoot, variants)
        }

        @JvmStatic
        fun `Semantics Test Model Verification Cases Should Pass`(): Stream<Arguments> {
            return argumentsFor(SEMANTICS_TEST)
        }

        @JvmStatic
        fun `Top Down Transition Test Model Verification Cases Should Pass`(): Stream<Arguments> {
            return argumentsFor(TOPDOWN, listOf(SysMLv2Variant.TopDown))
        }
    }

    private suspend fun assertPasses(
        sourcePath: Path,
        variant: SysMLv2Variant,
        case: VerificationCase,
    ) {
        val frontend = helper.buildFrontend(sourcePath, Portfolios.SmartFull, artifactRoot, variant)
        helper.checkConformance(
            frontend = frontend,
            case = case,
            validationPortfolio = Portfolios.AllAgree,
        )
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Semantics Test Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        assertPasses(SEMANTICS_TEST, variant, verificationCase)
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Top Down Transition Test Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        assertPasses(TOPDOWN, variant, verificationCase)
    }
}
