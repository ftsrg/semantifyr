/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.VerificationCase
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream

class SysMLWitnessTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLWitnessTests::class.java)

        private val MODELS = listOf(
            "door_access.sysml" to SysMLv2Variant.Default,
            "power_subsystems.sysml" to SysMLv2Variant.Default,
            "aircraft_engine.sysml" to SysMLv2Variant.Default,
            "autonomous_driving.sysml" to SysMLv2Variant.Default,
            "orion_protocol.sysml" to SysMLv2Variant.Default,
            "semanticstest.sysml" to SysMLv2Variant.Default,
            "topdowntransition.sysml" to SysMLv2Variant.TopDown,
        )

        @JvmStatic
        fun `Witnesses Re-verify Through All Backends`(): Stream<Arguments> {
            return MODELS.stream().flatMap { (name, variant) ->
                val sourcePath = SysMLv2FrontendTestHelper.modelPath(name)
                helper.discoverAsArguments(sourcePath, Portfolios.SmartFull, artifactRoot, listOf(variant)).map {
                    Arguments.of(Named.of(name, sourcePath), *it.get())
                }
            }
        }
    }

    @ParameterizedTest(name = "{0} - {1} - {2}")
    @MethodSource
    suspend fun `Witnesses Re-verify Through All Backends`(
        sourcePath: Path,
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        val frontend = helper.buildFrontend(sourcePath, Portfolios.SmartFull, artifactRoot, variant)
        helper.checkConformance(
            frontend = frontend,
            case = verificationCase,
            validationPortfolio = Portfolios.AllAgree,
        )
    }
}
