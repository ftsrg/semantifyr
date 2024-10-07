/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.compiler

import hu.bme.mit.semantifyr.oxsts.compiler.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.compiler.serialization.Serializer
import hu.bme.mit.semantifyr.oxsts.compiler.transformation.XstsTransformer
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Target
import org.junit.jupiter.api.Assertions
import org.slf4j.LoggerFactory
import java.io.File
import java.util.stream.Stream
import kotlin.streams.asStream

class TargetDefinition(
    val directory: String,
    val targetName: String,
    val library: String,
) {
    override fun toString(): String {
        return "$directory - $targetName"
    }
}

open class VerificationTest {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    companion object {
        val thetaVersion by EnvVar()

        val thetaExecutor = ThetaExecutor(
            thetaVersion,
            listOf(
                "--domain EXPL --refinement SEQ_ITP --maxenum 250 --initprec CTRL --stacktrace",
                "--domain EXPL_PRED_COMBINED --autoexpl NEWOPERANDS --initprec CTRL --stacktrace",
                "--domain PRED_CART --refinement SEQ_ITP --stacktrace",
                "--stacktrace",
            ),
        )

        fun streamTargetsFromFolder(
            directory: String,
            library: String,
            filter: (Target) -> Boolean = {
                it.name.contains("_Safe") || it.name.contains("_Unsafe")
            }
        ): Stream<TargetDefinition> {
            return File(directory).walkTopDown().filter {
                it.isDirectory
            }.filter {
                it.list { _, name -> name == "model.oxsts" }?.any() ?: false
            }.flatMap { file ->
                val reader = OxstsReader(library)
                reader.readModel("${file.path}/model.oxsts")

                reader.rootElements.asSequence().flatMap {
                    it.types
                }.filterIsInstance<Target>().filter {
                    !it.isAbstract
                }.filter(filter).map { target ->
                    TargetDefinition(file.path, target.name, library)
                }
            }.asStream()
        }
    }

    fun testVerification(targetDefinition: TargetDefinition) {
        val directory = targetDefinition.directory
        val library = targetDefinition.library
        val targetName = targetDefinition.targetName

        val targetDirectory = "$directory/artifacts/$targetName"

        File(targetDirectory).deleteRecursively()
        File(targetDirectory).mkdirs()

        transformTargetToTheta(directory, library, targetName, targetDirectory)

        val modelPath = "$targetDirectory/$targetName.xsts"

        logger.info("Executing theta on $modelPath")

        val result = thetaExecutor.run(targetDirectory, targetName)

        logger.info("Checking results of Theta")

        if (targetName.contains("Unsafe")) {
            Assertions.assertTrue(result.isUnsafe, "$targetName failed!")
        } else if (targetName.contains("Safe")) {
            Assertions.assertFalse(result.isUnsafe, "$targetName failed!")
        }
    }

    private fun transformTargetToTheta(modelDirectory: String, library: String, targetName: String, targetDirectory: String) {
        val reader = OxstsReader(library)
        reader.readDirectory(modelDirectory)

        val transformer = XstsTransformer(reader)
        val xsts = transformer.transform(targetName, true)
        val serializedXsts = Serializer.serialize(xsts)

        File("$targetDirectory/$targetName.xsts").writeText(serializedXsts)
    }
}
