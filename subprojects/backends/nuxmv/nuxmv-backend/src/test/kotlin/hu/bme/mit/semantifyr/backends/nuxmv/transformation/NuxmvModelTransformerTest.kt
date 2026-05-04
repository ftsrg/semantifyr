/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackendModule
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.Path

@InjectWithOxsts
class NuxmvModelTransformerTest {

    @Inject
    private lateinit var annotationHandler: BuiltinAnnotationHandler

    @Inject
    private lateinit var loader: SemantifyrLoader

    @Inject
    private lateinit var injector: Injector

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
            val artifacts = transform(invariant)
            assertThat(artifacts.property.invertVerdict).isFalse()
        }
    }

    @Test
    suspend fun `EF property maps to a negated invariant with verdict inversion`() {
        withVerificationScope {
            val incrementing = verificationCaseNamed("test-models/Simple/simple.oxsts", "Incrementing")
            val artifacts = transform(incrementing)
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
        return context.modelResources.asSequence().flatMap {
            it.contents
        }.filterIsInstance<OxstsModelPackage>().flatMap {
            it.declarations
        }.filterIsInstance<ClassDeclaration>().first {
            it.name == name
        }
    }

    private fun compileFirstVerificationCase(modelPath: String): NuxmvArtifacts {
        val context = loader
            .startContext()
            .loadModel(Path(modelPath))
            .buildAndResolve()
        val firstCase = context.modelResources.asSequence().flatMap {
            it.contents
        }.filterIsInstance<OxstsModelPackage>().flatMap {
            it.declarations
        }.filterIsInstance<ClassDeclaration>().first {
            annotationHandler.isVerificationCase(it)
        }

        return transform(firstCase)
    }

    private fun transform(classDeclaration: ClassDeclaration): NuxmvArtifacts {
        val artifactDir = Files.createTempDirectory("nuxmv-test-")
        val compiler = SemantifyrCompiler(injector, ArtifactConfig.NONE, OptimizationConfig.NONE)

        val backendInjector = injector.createChildInjector(NuxmvBackendModule())
        val transformer = backendInjector.getInstance(NuxmvModelTransformer::class.java)
        return transformer.transform(compiler.compile(classDeclaration, artifactDir).inlinedOxsts)
    }

}
