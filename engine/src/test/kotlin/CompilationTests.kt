import com.google.inject.Inject
import hu.bme.mit.gamma.oxsts.engine.reader.OxstsReader
import hu.bme.mit.gamma.oxsts.engine.reader.prepareOxsts
import hu.bme.mit.gamma.oxsts.engine.serialization.Serializer
import hu.bme.mit.gamma.oxsts.engine.transformation.XstsTransformer
import hu.bme.mit.gamma.oxsts.lang.tests.OxstsInjectorProvider
import hu.bme.mit.gamma.oxsts.model.oxsts.Package
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.extensions.InjectionExtension
import org.eclipse.xtext.testing.util.ParseHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.nio.charset.Charset
import java.util.stream.Stream
import kotlin.streams.asStream

const val baseDirectory = "Test Models"
const val automatedDirectory = "$baseDirectory/Automated"

@ExtendWith(InjectionExtension::class)
@InjectWith(OxstsInjectorProvider::class)
class CompilationTests {

    @Inject
    private lateinit var parser: ParseHelper<Package>

    companion object {
        @JvmStatic
        fun `Simple model transformations should not regress`(): Stream<String> {
            return File(automatedDirectory).walkTopDown().filter {
                it.isDirectory
            }.filter {
                it.list { _, name -> name == "model.oxsts" }?.any() ?: false
            }.map {
                it.path
            }.asStream()
        }

        @BeforeAll
        @JvmStatic
        fun prepare() {
            prepareOxsts()
        }

    }

    @ParameterizedTest
    @MethodSource
    fun `Simple model transformations should not regress`(directory: String) {
        simpleReadTransformWrite(directory)
        assertModelEqualsExpected(directory)
    }

    @Test
    fun test() {
        val directory = "$automatedDirectory/Simple/Inline Transition/Inline static recursion"

        simpleReadTransformWrite(directory)
        assertModelEqualsExpected(directory)
    }

    private fun simpleReadTransformWrite(directory: String) {
        val reader = OxstsReader(directory)
        reader.read()

        val transformer = XstsTransformer()
        val xsts = transformer.transform(reader.rootElements, "Mission")
        val serializedXsts = Serializer.serialize(xsts)

        File("$directory/model.xsts").writeText(serializedXsts)
    }

    private fun assertModelEqualsExpected(directory: String) {
        val expected = File("$directory/expected.xsts").readTextOrEmpty()
        val actual = File("$directory/model.xsts").readTextOrEmpty()
        Assertions.assertEquals(expected, actual)
    }

    private fun File.readTextOrEmpty(charset: Charset = Charsets.UTF_8) = if (exists()) {
        readText(charset)
    } else {
        "$$\$FILE_DOES_NOT_EXIST$$$"
    }

}
