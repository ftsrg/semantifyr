/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.library.ClasspathBasedOxstsLibrary
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SysMLLibraryRedefinitionTest {

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val loader = injector.getInstance(SemantifyrLoader::class.java)
    private val redefinitionHandler = injector.getInstance(RedefinitionHandler::class.java)

    @TempDir
    lateinit var extractionDirectory: Path

    // the OXSTS validator does not compare parameter lists of redefinitions yet
    @Test
    fun `redefined transitions keep the parameters of the transition they redefine`() {
        val library = ClasspathBasedOxstsLibrary(javaClass.classLoader, "default", extractionDirectory)
        val context = loader.startContext()
            .loadLibraryPaths(listOf(library.extractedLibraryPath))
            .buildAndResolve()

        val redefinitions = context.libraryResources.flatMap { resource ->
            resource.allContents.asSequence().filterIsInstance<TransitionDeclaration>().filter {
                it.isRedefine
            }.toList()
        }
        val mismatches = redefinitions.filter {
            val redefined = redefinitionHandler.getRedefinedDeclaration(it) as TransitionDeclaration
            it.parameters.size != redefined.parameters.size
        }.map {
            "${(it.eContainer() as NamedElement).name}::${it.name}"
        }

        assertThat(redefinitions).isNotEmpty()
        assertThat(mismatches).isEmpty()
    }
}
