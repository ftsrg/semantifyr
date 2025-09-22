/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.frontend

import hu.bme.mit.semantifyr.frontends.gamma.frontend.reader.GammaReader
import hu.bme.mit.semantifyr.frontends.gamma.frontend.reader.prepareGamma
import hu.bme.mit.semantifyr.frontends.gamma.frontend.serialization.GammaToOxstsSerializer
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Package
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.prepareOxsts
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

class SpacecraftTests : VerificationCaseTest() {

    companion object {
        val gammaFile = "TestModels/Spacecraft/Spacecraft.gamma"
        val oxstsFile = "TestModels/Spacecraft/Spacecraft.oxsts"

        lateinit var gammaModel: Package

        @JvmStatic
        @BeforeAll
        fun initialize() {
            prepareGamma()
            prepareOxsts()
            thetaExecutor.initTheta()

            val gammaReader = GammaReader()
            gammaModel = gammaReader.readGammaFile(File(gammaFile))
            val oxstsModel = GammaToOxstsSerializer.serialize(gammaModel)
            val outputFile = File(oxstsFile)
            outputFile.writeText(oxstsModel)
        }

        @JvmStatic
        fun `Verification cases should verify correctly`(): Stream<VerificationCaseDefinition> {
            return gammaModel.verificationCases.stream().map {
                VerificationCaseDefinition(
                    oxstsFile,
                    it,
                    "Library"
                )
            }
        }
    }

    @Test
    fun `Should not compile with no_par library`() {
        val verificationCaseDefinition = VerificationCaseDefinition(
            oxstsFile,
            gammaModel.verificationCases.stream().findAny().get(),
            "NoParRegionLibrary"
        )

        Assertions.assertThrowsExactly(IllegalStateException::class.java) {
            compileOxstsToXsts(verificationCaseDefinition)
        }
    }

    @Test
    fun `Should compile with normal library`() {
        val verificationCaseDefinition = VerificationCaseDefinition(
            oxstsFile,
            gammaModel.verificationCases.stream().findAny().get(),
            "Library"
        )

        val targetDirectory = verificationCaseDefinition.targetDirectory

        File(targetDirectory).deleteRecursively()
        File(targetDirectory).mkdirs()

        compileOxstsToXsts(verificationCaseDefinition)
    }

    @ParameterizedTest
    @MethodSource
    fun `Verification cases should verify correctly`(verificationCaseDefinition: VerificationCaseDefinition) {
        testVerification(verificationCaseDefinition)
    }

}
