/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class SpacecraftFalsificationTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SpacecraftFalsificationTests::class.java)
    }

    @Test
    suspend fun `SpacecraftDataCannotReach60Violated Should Fail`() {
        checkFalsification("spacecraft.sysml", "SpacecraftDataCannotReach60Violated")
    }

    @Test
    suspend fun `Compressed SpacecraftDataCannotReach60Violated Should Fail`() {
        checkFalsification("compressedspacecraft.sysml", "SpacecraftDataCannotReach60Violated")
    }

    @Test
    suspend fun `GroundStationReceivedDataCannotReach20Violated Should Fail`() {
        checkFalsification("spacecraft.sysml", "GroundStationReceivedDataCannotReach20Violated")
    }

    @Test
    suspend fun `Compressed GroundStationReceivedDataCannotReach20Violated Should Fail`() {
        checkFalsification("compressedspacecraft.sysml", "GroundStationReceivedDataCannotReach20Violated")
    }

    private suspend fun checkFalsification(modelFile: String, caseName: String) {
        val frontend = helper.buildFrontend(
            sourcePath = SysMLv2FrontendTestHelper.modelPath(modelFile),
            portfolio = Portfolios.ThetaFull,
            outputDirectory = artifactRoot,
            timeout = 300.seconds,
        )
        val case = frontend.verificationCases().single {
            it.qualifiedName.endsWith("::$caseName")
        }
        helper.checkVerificationCase(frontend, case)
    }
}
