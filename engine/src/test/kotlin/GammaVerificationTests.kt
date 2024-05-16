import hu.bme.mit.gamma.oxsts.engine.reader.prepareOxsts
import hu.bme.mit.gamma.oxsts.lang.tests.OxstsInjectorProvider
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.extensions.InjectionExtension
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private val baseDirectory = "Test Models/Automated/Gamma"
private val libraryDirectory = "Test Models/Automated/Gamma Semantic Library"

@ExtendWith(InjectionExtension::class)
@InjectWith(OxstsInjectorProvider::class)
class GammaVerificationTests : VerificationTest() {

    companion object {
        @JvmStatic
        fun `Crossroads Verification cases should pass`() = streamTargetsFromFolder("$baseDirectory/Crossroads", libraryDirectory)

        @JvmStatic
        fun `Simple Mission Verification cases should pass`() = streamTargetsFromFolder("$baseDirectory/SimpleMission", libraryDirectory)

        @JvmStatic
        fun `Spacecraft Verification cases should pass`() = streamTargetsFromFolder("$baseDirectory/Spacecraft", libraryDirectory)

        @BeforeAll
        @JvmStatic
        fun prepare() {
            prepareOxsts()
        }
    }

    @ParameterizedTest
    @MethodSource
    fun `Crossroads Verification cases should pass`(targetDefinition: TargetDefinition) {
        testVerification(targetDefinition)
    }

    @ParameterizedTest
    @MethodSource
    fun `Simple Mission Verification cases should pass`(targetDefinition: TargetDefinition) {
        testVerification(targetDefinition)
    }

    @ParameterizedTest
    @MethodSource
    fun `Spacecraft Verification cases should pass`(targetDefinition: TargetDefinition) {
        testVerification(targetDefinition)
    }

}
