/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.oxsts.engine.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.engine.serialization.Serializer
import hu.bme.mit.semantifyr.oxsts.engine.transformation.XstsTransformer
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Target
import org.junit.jupiter.api.Assertions
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
    companion object {
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
                val reader = OxstsReader("${file.path}/model.oxsts", library)
                reader.read()

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
        val propertyPath = "$targetDirectory/$targetName.prop"
        val tracePath = "$targetDirectory/$targetName.cex"

        executeTheta(modelPath, propertyPath, tracePath)

        if (targetName.contains("Unsafe")) {
            Assertions.assertTrue(File(tracePath).exists(), "$targetName failed!")
        } else if (targetName.contains("Safe")) {
            Assertions.assertFalse(File(tracePath).exists(), "$targetName failed!")
        }
    }

    private fun executeTheta(
        modelPath: String, propertyPath: String, tracePath: String,
        timeout: Long = 60, timeUnit: TimeUnit = TimeUnit.MINUTES
    ) {
        val process1 = ProcessBuilder(
            "java",
            "-jar", "theta/theta-xsts-cli.jar",
            "--domain", "EXPL",
            "--refinement", "SEQ_ITP",
            "--maxenum", "250",
            "--initprec", "CTRL",
            "--model", modelPath,
            "--property", propertyPath,
            "--cex", tracePath,
            "--stacktrace",
        )
            .inheritIO()
            .start()


        val process2 = ProcessBuilder(
            "java",
            "-jar", "theta/theta-xsts-cli.jar",
            "--domain", "EXPL_PRED_COMBINED",
            "--autoexpl", "NEWOPERANDS ",
            "--initprec", "CTRL",
            "--model", modelPath,
            "--property", propertyPath,
            "--cex", tracePath,
            "--stacktrace",
        )
            .inheritIO()
            .start()

        val future1 = CompletableFuture.supplyAsync {
            process1.waitFor(timeout, timeUnit)
        }

        val future2 = CompletableFuture.supplyAsync {
            process2.waitFor(timeout, timeUnit)
        }

        CompletableFuture.anyOf(future1, future2).join()

        process1.destroy()
        process2.destroy()

        if (!future1.getNow(true) && !future1.getNow(true)) {
            throw TimeoutException("Verification stopped due to reaching timeout $timeout $timeUnit")
        }
    }

    private fun transformTargetToTheta(modelDirectory: String, library: String, targetName: String, targetDirectory: String) {
        val reader = OxstsReader(modelDirectory, library)
        reader.read()

        val transformer = XstsTransformer(reader)
        val xsts = transformer.transform(targetName, true)
        val serializedXsts = Serializer.serialize(xsts, false)

        File("$targetDirectory/$targetName.xsts").writeText(serializedXsts)

        val serializedProperty = Serializer.serializeProperty(xsts)

        File("$targetDirectory/$targetName.prop").writeText(serializedProperty)
    }
}
