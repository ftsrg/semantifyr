/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.sysml.semantics.SysMLv2Variant
import hu.bme.mit.semantifyr.frontends.sysml.semantics.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.VerificationCase
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class SimpleSysMLVerificationTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SimpleSysMLVerificationTests::class.java)

        private val DOOR_ACCESS = SysMLv2FrontendTestHelper.modelPath("door_access.sysml")

        @JvmStatic
        fun `Door Access Model Verification Cases Should Pass`(): Stream<Arguments> {
            return helper.discoverAsArguments(DOOR_ACCESS, Portfolios.SmartFull, artifactRoot)
        }
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Door Access Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        val frontend = helper.buildFrontend(DOOR_ACCESS, Portfolios.SmartFull, artifactRoot, variant)
        helper.checkConformance(frontend, verificationCase, Portfolios.AllAgree)
    }
}
