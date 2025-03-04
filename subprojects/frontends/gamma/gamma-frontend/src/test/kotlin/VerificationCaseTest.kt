/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.frontend

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCase
import hu.bme.mit.semantifyr.oxsts.semantifyr.VerificationTest
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.serialization.XstsSerializer
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.XstsTransformer
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import org.junit.jupiter.api.Assertions
import java.io.File

data class VerificationCaseDefinition(
    val serializedOxstsPath: String,
    val verificationCase: VerificationCase,
    val library: String
) {
    val name = verificationCase.name
    val targetDirectory = "TestModels/artifacts/$name"
    val xstsPath = "$targetDirectory/$name.xsts"
}

open class VerificationCaseTest : VerificationTest() {

    private val logger by loggerFactory()

    fun compileOxstsToXsts(verificationCaseDefinition: VerificationCaseDefinition) {
        val oxstsReader = OxstsReader(verificationCaseDefinition.library)
        oxstsReader.readModel(verificationCaseDefinition.serializedOxstsPath)

        val transformer = XstsTransformer(oxstsReader)

        val xsts = transformer.transform(verificationCaseDefinition.name, rewriteChoice = true)
        val xstsString = XstsSerializer.serialize(xsts)

        val xstsFile = File(verificationCaseDefinition.xstsPath)
        xstsFile.writeText(xstsString)
    }

    fun testVerification(verificationCaseDefinition: VerificationCaseDefinition) {
        val verificationCaseName = verificationCaseDefinition.name
        val targetDirectory = verificationCaseDefinition.targetDirectory

        File(targetDirectory).deleteRecursively()
        File(targetDirectory).mkdirs()

        compileOxstsToXsts(verificationCaseDefinition)

        val result = thetaExecutor.run(targetDirectory, verificationCaseDefinition.name)

        logger.info("Checking results of Theta")

        if (verificationCaseName.contains("Reachable")) {
            Assertions.assertTrue(result.isUnsafe, "$verificationCaseName failed!")
        } else {
            Assertions.assertFalse(result.isUnsafe, "$verificationCaseName failed!")
        }
    }

}
