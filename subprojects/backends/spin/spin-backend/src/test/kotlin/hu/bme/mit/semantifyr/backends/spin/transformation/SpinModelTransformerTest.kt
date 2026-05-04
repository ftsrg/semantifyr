/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackendModule
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.Path

@InjectWithOxsts
class SpinModelTransformerTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var annotationHandler: BuiltinAnnotationHandler

    @Inject
    private lateinit var loader: SemantifyrLoader

    private val childInjector by lazy {
        injector.createChildInjector(SpinBackendModule())
    }


    @Test
    suspend fun `model emits an init proctype with a main do-loop`() {
        withVerificationScope {
            val artifacts = compileFirstVerificationCase("test-models/Simple/simple.oxsts")
            assertThat(artifacts.promela).contains("init {")
            assertThat(artifacts.promela).contains("do")
            assertThat(artifacts.promela).contains("od")
        }
    }

    @Test
    suspend fun `integer variables map to Promela int`() {
        withVerificationScope {
            val artifacts = compileFirstVerificationCase("test-models/Simple/simple.oxsts")
            assertThat(artifacts.promela).containsPattern("int [^;]*x")
        }
    }

    @Test
    suspend fun `AG property emits a direct LTL claim without verdict inversion`() {
        withVerificationScope {
            val invariant = verificationCaseNamed("test-models/Simple/simple.oxsts", "Invariant")
            val artifacts = compile(invariant)
            assertThat(artifacts.property.invertVerdict).isFalse()
            assertThat(artifacts.property.ltl).startsWith("[]")
        }
    }

    @Test
    suspend fun `EF property emits a negated LTL invariant with verdict inversion`() {
        withVerificationScope {
            val incrementing = verificationCaseNamed("test-models/Simple/simple.oxsts", "Incrementing")
            val artifacts = compile(incrementing)
            assertThat(artifacts.property.invertVerdict).isTrue()
            assertThat(artifacts.property.ltl).startsWith("[] !")
        }
    }

    @Test
    suspend fun `boolean if-then-else inside LTL is rewritten as boolean composition`() {
        withVerificationScope {
            val case = verificationCaseNamed("test-models/IfThenElse/ite.oxsts", "IteInProperty")
            val artifacts = compile(case)
            // Promela's LTL has no ternary; the transformer must avoid `c -> a : b`
            // and emit a boolean composition that LTL can parse.
            assertThat(artifacts.property.ltl).doesNotContain(" -> (true) :")
            assertThat(artifacts.property.ltl).contains("&&").contains("||")
        }
    }

    @Test
    suspend fun `boolean if-then-else inside a transition keeps Promela ternary form`() {
        withVerificationScope {
            val case = verificationCaseNamed("test-models/IfThenElse/ite.oxsts", "IteInTransition")
            val artifacts = compile(case)
            // Inside the init proctype's transition body, the ternary form is the natural
            // Promela expression for a value-producing if-then-else.
            assertThat(artifacts.promela).containsPattern("\\) -> \\([^)]*\\) : \\(")
        }
    }

    @Test
    suspend fun `non-boolean if-then-else inside LTL throws BackendUnsupportedException`() {
        withVerificationScope {
            val case = verificationCaseNamed("test-models/IfThenElse/ite.oxsts", "IteInPropertyNonBoolean")
            assertThatThrownBy { compile(case) }
                .isInstanceOf(BackendUnsupportedException::class.java)
                .hasMessageContaining("non-boolean if-then-else")
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

    private fun compileFirstVerificationCase(modelPath: String): SpinArtifacts {
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
        return compile(firstCase)
    }

    private fun compile(classDeclaration: ClassDeclaration): SpinArtifacts {
        val artifactDir = Files.createTempDirectory("spin-test-")
        val compiler = SemantifyrCompiler(childInjector, ArtifactConfig.NONE, OptimizationConfig.NONE)
        val transformer = childInjector.getInstance(SpinModelTransformer::class.java)
        return transformer.transform(compiler.compile(classDeclaration, artifactDir).inlinedOxsts)
    }
}
