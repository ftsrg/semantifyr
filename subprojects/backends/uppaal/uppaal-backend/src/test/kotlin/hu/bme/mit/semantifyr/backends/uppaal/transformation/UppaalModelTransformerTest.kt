/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackendModule
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.Path

@InjectWithOxsts
class UppaalModelTransformerTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var annotationHandler: BuiltinAnnotationHandler

    @Inject
    private lateinit var loader: SemantifyrLoader

    private val childInjector by lazy {
        injector.createChildInjector(UppaalBackendModule())
    }

    @Test
    suspend fun `clock annotation produces a clock declaration`() {
        withVerificationScope {
            val artifacts = compileFirstVerificationCase("test-models/Timed/timed.oxsts")
            assertThat(artifacts.modelXml).containsPattern("clock [^;]*t[,;]")
            assertThat(artifacts.modelXml).doesNotContainPattern("int [^;]*t\\s*[=;]")
        }
    }

    @Test
    suspend fun `non-clock integer variables remain integers`() {
        withVerificationScope {
            val artifacts = compileFirstVerificationCase("test-models/Simple/simple.oxsts")
            assertThat(artifacts.modelXml).containsPattern("int [^;]*x\\s*[=;]")
            assertThat(artifacts.modelXml).doesNotContainPattern("clock [^;]*x[,;]")
        }
    }

    @Test
    suspend fun `EF property becomes an Uppaal reachability query`() {
        withVerificationScope {
            val artifacts = compileFirstVerificationCase("test-models/Simple/simple.oxsts")
            assertThat(artifacts.query).startsWith("E<>")
        }
    }

    @Test
    fun `annotation handler recognizes the Clock annotation`() {
        val case = firstVerificationCase("test-models/Timed/timed.oxsts")
        val hasClockVar = case.members.filterIsInstance<VariableDeclaration>().any {
            annotationHandler.isClockVariable(it)
        }
        assertThat(hasClockVar).isTrue()
    }

    private fun firstVerificationCase(modelPath: String): ClassDeclaration {
        val context = loader
            .startContext()
            .loadModel(Path(modelPath))
            .buildAndResolve()
        return context.modelResources.asSequence().flatMap {
            it.contents
        }.filterIsInstance<OxstsModelPackage>().flatMap {
            it.declarations
        }.filterIsInstance<ClassDeclaration>().first {
            annotationHandler.isVerificationCase(it)
        }
    }

    private fun compileFirstVerificationCase(modelPath: String): UppaalArtifacts {
        val classDeclaration = firstVerificationCase(modelPath)
        val artifactDir = Files.createTempDirectory("uppaal-test-")
        val compiler = SemantifyrCompiler(childInjector, ArtifactConfig.NONE, OptimizationConfig.NONE)
        val generator = childInjector.getInstance(UppaalModelTransformer::class.java)
        val compiled = compiler.compile(classDeclaration, artifactDir)
        return generator.generate(compiled.inlinedOxsts)
    }
}
