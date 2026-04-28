/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackendModule
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.Path

class NuxmvModelGeneratorTest {
    private val injector = OxstsStandaloneSetup()
        .createInjectorAndDoEMFRegistration()
        .createChildInjector(NuxmvBackendModule)
    private val annotationHandler: BuiltinAnnotationHandler = injector.getInstance(BuiltinAnnotationHandler::class.java)
    private val loader: SemantifyrLoader = injector.getInstance(SemantifyrLoader::class.java)

    @Test
    suspend fun `integer variables become unbounded SMV integers`() {
        withVerificationScope {
            val artifacts = compileFirstVerificationCase("test-models/Simple/simple.oxsts")
            assertThat(artifacts.smv).containsPattern(": integer;")
        }
    }

    @Test
    suspend fun `init transition populates INIT section`() {
        withVerificationScope {
            val artifacts = compileFirstVerificationCase("test-models/Simple/simple.oxsts")
            assertThat(artifacts.smv).contains("INIT")
            assertThat(artifacts.smv).contains("TRANS")
            assertThat(artifacts.smv).contains("MODULE main")
        }
    }

    @Test
    suspend fun `AG property maps directly to an invariant without verdict inversion`() {
        withVerificationScope {
            val invariant = verificationCaseNamed("test-models/Simple/simple.oxsts", "Invariant")
            val artifacts = compile(invariant)
            assertThat(artifacts.property.invertVerdict).isFalse()
        }
    }

    @Test
    suspend fun `EF property maps to a negated invariant with verdict inversion`() {
        withVerificationScope {
            val incrementing = verificationCaseNamed("test-models/Simple/simple.oxsts", "Incrementing")
            val artifacts = compile(incrementing)
            assertThat(artifacts.property.invertVerdict).isTrue()
            assertThat(artifacts.property.invariant).startsWith("!(")
        }
    }

    private fun verificationCaseNamed(
        modelPath: String,
        name: String,
    ): ClassDeclaration {
        val context = loader
            .startContext()
            .loadModel(Path(modelPath))
            .buildAndResolve()
        return context.modelResources
            .flatMap { it.contents }
            .filterIsInstance<OxstsModelPackage>()
            .flatMap { it.declarations }
            .filterIsInstance<ClassDeclaration>()
            .first { it.name == name }
    }

    private fun compileFirstVerificationCase(modelPath: String): NuxmvArtifacts {
        val context = loader
            .startContext()
            .loadModel(Path(modelPath))
            .buildAndResolve()
        val firstCase = context.modelResources
            .flatMap { it.contents }
            .filterIsInstance<OxstsModelPackage>()
            .flatMap { it.declarations }
            .filterIsInstance<ClassDeclaration>()
            .first { annotationHandler.isVerificationCase(it) }
        return compile(firstCase)
    }

    private fun compile(classDeclaration: ClassDeclaration): NuxmvArtifacts {
        val artifactDir = Files.createTempDirectory("nuxmv-test-")
        SemantifyrCompiler(injector, ArtifactConfig.NONE, OptimizationConfig.NONE).use { compiler ->
            val generator = injector.getInstance(NuxmvModelGenerator::class.java)
            return generator.generate(compiler.compile(classDeclaration, artifactDir).inlinedOxsts)
        }
    }
}
