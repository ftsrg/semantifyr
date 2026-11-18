/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.RestoreGlobalRegistries
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.resource.FileExtensionProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

@RestoreGlobalRegistries
class SemantifyrLoaderExtensionTest {

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val loader = injector.getInstance(SemantifyrLoader::class.java)
    private val extensions = injector.getInstance(FileExtensionProvider::class.java).fileExtensions

    @TempDir
    lateinit var directory: Path

    @Test
    fun `a directory mixing the extensions loads a model from each file and skips the other files`() {
        for (extension in extensions) {
            directory.resolve("model.$extension").writeText("package from_$extension\nclass Element\n")
        }
        directory.resolve("notes.txt").writeText("not a model")

        val context = loader.startContext().loadModels(directory).buildAndResolve()

        assertThat(
            context.modelResources.map {
                it.uri.lastSegment()
            },
        ).containsExactlyInAnyOrderElementsOf(
            extensions.map {
                "model.$it"
            },
        )
    }

    @Test
    fun `a directory mixing the extensions loads a library from each file`() {
        for (extension in extensions) {
            directory.resolve("library.$extension").writeText("package from_$extension\nclass Element\n")
        }

        val context = loader.startContext().loadLibraries(directory).buildAndResolve()

        assertThat(
            context.libraryResources.map {
                it.uri.lastSegment()
            },
        ).containsExactlyInAnyOrderElementsOf(
            extensions.map {
                "library.$it"
            },
        )
    }
}
