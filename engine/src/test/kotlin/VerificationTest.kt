import hu.bme.mit.gamma.oxsts.engine.reader.OxstsReader
import hu.bme.mit.gamma.oxsts.engine.serialization.Serializer
import hu.bme.mit.gamma.oxsts.engine.transformation.XstsTransformer
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import org.junit.jupiter.api.Assertions
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.streams.asStream

class TargetDefinition(
    val directory: String,
    val target: Target
) {
    override fun toString(): String {
        return "$directory - ${target.name}"
    }
}

open class VerificationTest(
    val directory: String
) {
    companion object {
        fun streamTargetsFromFolder(directory: String): Stream<TargetDefinition> {
            return File(directory).walkTopDown().filter {
                it.isDirectory
            }.filter {
                it.list { _, name -> name == "model.oxsts" }?.any() ?: false
            }.flatMap { file ->
                val reader = OxstsReader("${file.path}/model.oxsts")
                reader.read()

                reader.rootElements.asSequence().flatMap {
                    it.types
                }.filterIsInstance<Target>().filter {
                    !it.isAbstract
                }.filter {
                    it.name.contains("_Safe") || it.name.contains("_Unsafe")
                }.map { target ->
                    TargetDefinition(file.path, target)
                }
            }.asStream()
        }
    }



    fun testVerification(targetDefinition: TargetDefinition) {
        val directory = targetDefinition.directory
        val target = targetDefinition.target

        val targetDirectory = "$directory/artifacts/${target.name}"

        File(targetDirectory).deleteRecursively()
        File(targetDirectory).mkdirs()

        transformTargetToTheta(targetDirectory, target)

        val modelPath = "$targetDirectory/${target.name}.xsts"
        val propertyPath = "$targetDirectory/${target.name}.prop"
        val tracePath = "$targetDirectory/${target.name}.cex"

        executeTheta(modelPath, propertyPath, tracePath)

        if (target.name.contains("Unsafe")) {
            Assertions.assertTrue(File(tracePath).exists(), "${target.name} failed!")
        } else if (target.name.contains("Safe")) {
            Assertions.assertFalse(File(tracePath).exists(), "${target.name} failed!")
        }
    }

    private fun executeTheta(modelPath: String, propertyPath: String, tracePath: String) {
        val process = ProcessBuilder(
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
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        process.waitFor(60, TimeUnit.MINUTES)
    }

    private fun transformTargetToTheta(directory: String, target: Target) {
        val transformer = XstsTransformer()
        val xsts = transformer.transform(target, true)
        val serializedXsts = Serializer.serialize(xsts, false)

        File("$directory/${target.name}.xsts").writeText(serializedXsts)

        val serializedProperty = Serializer.serializeProperty(xsts)

        File("$directory/${target.name}.prop").writeText(serializedProperty)
    }
}
