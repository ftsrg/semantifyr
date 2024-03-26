import hu.bme.mit.gamma.oxsts.engine.reader.prepareOxsts
import hu.bme.mit.gamma.oxsts.lang.tests.OxstsInjectorProvider
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.extensions.InjectionExtension
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private val modelDirectory = "Test Models/Automated/Gamma/Spacecraft"

@ExtendWith(InjectionExtension::class)
@InjectWith(OxstsInjectorProvider::class)
class SpacecraftVerificationTest : VerificationTest(modelDirectory) {

    companion object {
        @JvmStatic
        fun `Spacecraft Verification cases should pass`() = streamTargetsFromFolder(modelDirectory)

        @BeforeAll
        @JvmStatic
        fun prepare() {
            prepareOxsts()
        }
    }

    @ParameterizedTest
    @MethodSource
    fun `Spacecraft Verification cases should pass`(targetDefinition: TargetDefinition) {
        testVerification(targetDefinition)
    }
}
