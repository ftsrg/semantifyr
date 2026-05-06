/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.VerificationCase
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream

class SysMLVerificationTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLVerificationTests::class.java)

        private val CROSSROADS = SysMLv2FrontendTestHelper.modelPath("crossroads.sysml")
        private val COMPRESSED_SPACECRAFT = SysMLv2FrontendTestHelper.modelPath("compressedspacecraft.sysml")
        private val SPACECRAFT = SysMLv2FrontendTestHelper.modelPath("spacecraft.sysml")
        private val POWER_SUBSYSTEMS = SysMLv2FrontendTestHelper.modelPath("power_subsystems.sysml")
        private val AIRCRAFT_ENGINE = SysMLv2FrontendTestHelper.modelPath("aircraft_engine.sysml")
        private val AUTONOMOUS_DRIVING = SysMLv2FrontendTestHelper.modelPath("autonomous_driving.sysml")
        private val ORION_PROTOCOL = SysMLv2FrontendTestHelper.modelPath("orion_protocol.sysml")

        private fun argumentsFor(sourcePath: Path): Stream<Arguments> {
            return helper.discoverAsArguments(sourcePath, Portfolios.SmartFull, artifactRoot)
        }

        @JvmStatic
        fun `Crossroads Model Verification Cases Should Pass`(): Stream<Arguments> {
            return argumentsFor(CROSSROADS)
        }

        @JvmStatic
        fun `Compressed Spacecraft Model Verification Cases Should Pass`(): Stream<Arguments> {
            return argumentsFor(COMPRESSED_SPACECRAFT)
        }

        @JvmStatic
        fun `Full Spacecraft Model Verification Cases Should Pass`(): Stream<Arguments> {
            return argumentsFor(SPACECRAFT)
        }

        @JvmStatic
        fun `Power Subsystems Model Verification Cases Should Pass`(): Stream<Arguments> {
            return argumentsFor(POWER_SUBSYSTEMS)
        }

        @JvmStatic
        fun `Aircraft Engine Model Verification Cases Should Pass`(): Stream<Arguments> {
            return argumentsFor(AIRCRAFT_ENGINE)
        }

        @JvmStatic
        fun `Autonomous Driving Model Verification Cases Should Pass`(): Stream<Arguments> {
            return argumentsFor(AUTONOMOUS_DRIVING)
        }

        @JvmStatic
        fun `Orion Protocol Model Verification Cases Should Pass`(): Stream<Arguments> {
            return argumentsFor(ORION_PROTOCOL)
        }
    }

    private suspend fun assertPassed(
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
    suspend fun `Crossroads Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        assertPassed(CROSSROADS, variant, verificationCase)
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Compressed Spacecraft Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        assertPassed(COMPRESSED_SPACECRAFT, variant, verificationCase)
    }

    @Disabled
    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Full Spacecraft Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        assertPassed(SPACECRAFT, variant, verificationCase)
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Power Subsystems Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        assertPassed(POWER_SUBSYSTEMS, variant, verificationCase)
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Aircraft Engine Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        assertPassed(AIRCRAFT_ENGINE, variant, verificationCase)
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Autonomous Driving Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        assertPassed(AUTONOMOUS_DRIVING, variant, verificationCase)
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Orion Protocol Model Verification Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        assertPassed(ORION_PROTOCOL, variant, verificationCase)
    }
}
