/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class FalsificationCaseTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(FalsificationCaseTests::class.java)
    }

    @Test
    suspend fun `EngineDownUnreachableViolated Should Fail`() {
        checkFalsification("aircraft_engine.sysml", "EngineDownUnreachableViolated")
    }

    @Test
    suspend fun `CollisionAvoidanceActiveUnreachableViolated Should Fail`() {
        checkFalsification("autonomous_driving.sysml", "CollisionAvoidanceActiveUnreachableViolated")
    }

    @Test
    suspend fun `ControllerInterruptedUnreachableViolated Should Fail`() {
        checkFalsification("crossroads.sysml", "ControllerInterruptedUnreachableViolated")
    }

    @Test
    suspend fun `UnreachableRedGreenViolated Should Fail`() {
        checkFalsification("crossroads.sysml", "UnreachableRedGreenViolated")
    }

    @Test
    suspend fun `OpenUnreachableViolated Should Fail`() {
        checkFalsification("door_access.sysml", "OpenUnreachableViolated")
    }

    @Test
    suspend fun `MasterConnectedUnreachableViolated Should Fail`() {
        checkFalsification("orion_protocol.sysml", "MasterConnectedUnreachableViolated")
    }

    @Test
    suspend fun `SlaveConnectedUnreachableViolated Should Fail`() {
        checkFalsification("orion_protocol.sysml", "SlaveConnectedUnreachableViolated")
    }

    @Test
    suspend fun `LowPowerUnreachableViolated Should Fail`() {
        checkFalsification("power_subsystems.sysml", "LowPowerUnreachableViolated")
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
    suspend fun `NominalUnreachableViolated Should Fail`() {
        checkFalsification("aircraft_engine.sysml", "NominalUnreachableViolated")
    }

    @Test
    suspend fun `NominalAndIdleSimultaneousViolated Should Fail`() {
        checkFalsification("aircraft_engine.sysml", "NominalAndIdleSimultaneousViolated")
    }

    @Test
    suspend fun `DoneUnreachableViolated Should Fail`() {
        checkFalsification("autonomous_driving.sysml", "DoneUnreachableViolated")
    }

    @Test
    suspend fun `TrafficLightABehaviorNormalGreenUnreachableViolated Should Fail`() {
        checkFalsification("crossroads.sysml", "TrafficLightABehaviorNormalGreenUnreachableViolated")
    }

    @Test
    suspend fun `CheckingUnreachableViolated Should Fail`() {
        checkFalsification("door_access.sysml", "CheckingUnreachableViolated")
    }

    @Test
    suspend fun `LockedAndCheckingSimultaneousViolated Should Fail`() {
        checkFalsification("door_access.sysml", "LockedAndCheckingSimultaneousViolated")
    }

    @Test
    suspend fun `BothMasterAndSlaveConnectedUnreachableViolated Should Fail`() {
        checkFalsification("orion_protocol.sysml", "BothMasterAndSlaveConnectedUnreachableViolated")
    }

    @Test
    suspend fun `OnUnreachableViolated Should Fail`() {
        checkFalsification("power_subsystems.sysml", "OnUnreachableViolated")
    }

    @Test
    suspend fun `OnAndOffSimultaneousViolated Should Fail`() {
        checkFalsification("power_subsystems.sysml", "OnAndOffSimultaneousViolated")
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
            outputDirectory = artifactRoot.resolve("$modelFile-$caseName"),
            timeout = 300.seconds,
        )
        val case = frontend.verificationCases().single {
            it.qualifiedName.endsWith("::$caseName")
        }
        helper.checkVerificationCase(frontend, case)
    }
}
