import hu.bme.mit.gamma.oxsts.engine.reader.OxstsReader
import hu.bme.mit.gamma.oxsts.engine.reader.prepareOxsts
import hu.bme.mit.gamma.oxsts.engine.serialization.Serializer
import hu.bme.mit.gamma.oxsts.engine.transformation.XstsTransformer
import hu.bme.mit.gamma.oxsts.lang.tests.OxstsInjectorProvider
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.extensions.InjectionExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

val directory = "Test Models/Automated/Gamma/Crossroads"
val artifactsDirectory = "$directory/artifacts"

@ExtendWith(InjectionExtension::class)
@InjectWith(OxstsInjectorProvider::class)
class CrossroadsTest {

    companion object {
        @JvmStatic
        fun `Crossroads model verification case should pass`(): Stream<Target> {
            val reader = OxstsReader(directory)
            reader.read()

            val targets = reader.rootElements.flatMap {
                it.types
            }.filterIsInstance<Target>().filter {
                !it.isAbstract
            }

            return targets.stream()
        }

        @BeforeAll
        @JvmStatic
        fun prepare() {
            prepareOxsts()

            File(artifactsDirectory).deleteRecursively()
        }
    }

    @ParameterizedTest
    @MethodSource
    fun `Crossroads model verification case should pass`(target: Target) {
        val targetDirectory = "$artifactsDirectory/${target.name}"

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
