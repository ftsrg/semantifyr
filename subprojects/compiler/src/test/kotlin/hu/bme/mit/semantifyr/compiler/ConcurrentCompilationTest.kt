/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.VerificationCaseFinder
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

class ConcurrentCompilationTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    private val model = Path("../../oxsts-test-models/tutorial/advanced.oxsts")

    private val copiesOfEachCase = 4

    @Test
    suspend fun `cases compiled at once on a fresh injector give the artifacts they give one after the other`() {
        val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        val compiler = SemantifyrCompiler(injector, ArtifactConfig())
        val caseNames = loadCases(injector).map {
            it.name
        }
        // a compilation adds its inlined model to the resource set, so each one gets its own, as each verification does
        val concurrentCompilations = (1..copiesOfEachCase).flatMap { copy ->
            caseNames.map {
                ConcurrentCompilation(loadCase(injector, it), temporaryDirectory.resolve("concurrent").resolve("$it-$copy"))
            }
        }

        coroutineScope {
            concurrentCompilations.map {
                async(Dispatchers.Default) {
                    compiler.compile(it.case, it.outputDirectory)
                }
            }.awaitAll()
        }
        for (name in caseNames) {
            compiler.compile(loadCase(injector, name), temporaryDirectory.resolve("sequential").resolve(name))
        }

        assertThat(caseNames).hasSizeGreaterThan(1)
        for (compilation in concurrentCompilations) {
            assertThat(artifactsUnder(compilation.outputDirectory))
                .describedAs("artifacts of ${compilation.outputDirectory.fileName}")
                .isNotEmpty()
                .isEqualTo(artifactsUnder(temporaryDirectory.resolve("sequential").resolve(compilation.case.name)))
        }
    }

    private class ConcurrentCompilation(
        val case: ClassDeclaration,
        val outputDirectory: Path,
    )

    private fun loadCase(injector: Injector, name: String): ClassDeclaration {
        return loadCases(injector).single {
            it.name == name
        }
    }

    private fun loadCases(injector: Injector): List<ClassDeclaration> {
        val context = injector.getInstance(SemantifyrLoader::class.java).loadStandaloneModel(model)
        val caseFinder = injector.getInstance(VerificationCaseFinder::class.java)
        return context.modelResources.flatMap {
            it.contents
        }.filterIsInstance<OxstsModelPackage>().flatMap {
            caseFinder.streamTestCases(it).toList()
        }
    }

    private fun artifactsUnder(directory: Path): Map<String, String> {
        return directory.walk().filter {
            it.isRegularFile() && it.toString().endsWith(".oxsts")
        }.associate {
            it.relativeTo(directory).toString() to it.readText()
        }
    }
}
