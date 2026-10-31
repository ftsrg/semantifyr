/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.library.ClasspathBasedOxstsLibrary
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class SysMLVariantTest {

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val loader = injector.getInstance(SemantifyrLoader::class.java)

    @TempDir
    lateinit var directory: Path

    private fun packageOf(element: EObject): String {
        return (element.eContainer() as OxstsModelPackage).name
    }

    private fun stateSuperTypePackage(vararg imports: String): String {
        val library = ClasspathBasedOxstsLibrary(javaClass.classLoader, "default", directory.resolve("library"))
        val model = directory.resolve("Probe.oxsts")
        model.writeText("package probe\n\n${imports.joinToString("\n") { "import $it" }}\n\nclass Probe : State\n")
        val context = loader.startContext()
            .loadLibraryPaths(listOf(library.extractedLibraryPath))
            .loadModel(model)
            .buildAndResolve()
        val probe = context.modelResources.single().allContents.asSequence().filterIsInstance<ClassDeclaration>().single()
        return packageOf(probe.superTypes.single())
    }

    @Test
    fun `the first import that declares a class wins`() {
        assertThat(stateSuperTypePackage("semantifyr::sysml::topdown", "semantifyr::sysml::states")).isEqualTo("semantifyr::sysml::topdown")
        assertThat(stateSuperTypePackage("semantifyr::sysml::states", "semantifyr::sysml::topdown")).isEqualTo("semantifyr::sysml::states")
    }

    @Test
    fun `the topdown variant types every state of the model with the variant's state`() {
        val frontend = SysMLv2FrontendTestHelper().buildFrontend(
            sourcePath = SysMLv2FrontendTestHelper.modelPath("topdowntransition.sysml"),
            portfolio = SingleBackendPortfolio(FakeBackend.verdict("fake", VerificationVerdict.Passed), FakeConfig()),
            outputDirectory = directory.resolve("frontend"),
            variant = SysMLv2Variant.TopDown,
        )
        val stateTypes = frontend.modelContext.modelResources.flatMap { resource ->
            resource.allContents.asSequence().filterIsInstance<FeatureDeclaration>().mapNotNull {
                it.typeSpecification?.domain
            }.filter {
                it is ClassDeclaration && it.name == "State"
            }.toList()
        }

        assertThat(stateTypes).isNotEmpty()
        assertThat(stateTypes.map { packageOf(it) }.toSet()).containsExactly("semantifyr::sysml::topdown")
    }
}
