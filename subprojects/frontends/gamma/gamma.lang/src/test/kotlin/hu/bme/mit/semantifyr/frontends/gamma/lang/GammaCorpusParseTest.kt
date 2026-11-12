/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.diagnostics.Severity
import org.eclipse.xtext.resource.IResourceFactory
import org.eclipse.xtext.resource.IResourceServiceProvider
import org.eclipse.xtext.resource.XtextResource
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

@InjectWithGamma
class GammaCorpusParseTest {

    @Inject
    private lateinit var resourceSetProvider: Provider<XtextResourceSet>

    @Inject
    private lateinit var resourceFactory: IResourceFactory

    @Inject
    private lateinit var resourceServiceProvider: IResourceServiceProvider

    @Test
    fun `every in-set corpus file parses, links and validates without errors`() {
        IResourceServiceProvider.Registry.INSTANCE.extensionToFactoryMap[GAMMA_EXTENSION] = resourceServiceProvider
        val resourceSet = resourceSetProvider.get()
        resourceSet.resourceFactoryRegistry.extensionToFactoryMap[GAMMA_EXTENSION] = resourceFactory
        val files = Files.walk(CORPUS).use { paths ->
            paths.filter {
                it.extension == GAMMA_EXTENSION
            }.sorted().toList()
        }
        val resources = files.associateWith {
            resourceSet.getResource(URI.createFileURI(it.absolutePathString()), true) as XtextResource
        }
        EcoreUtil2.resolveAll(resourceSet)

        val errors = resources.flatMap { (file, resource) ->
            val validator = resource.resourceServiceProvider.resourceValidator
            validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl).filter {
                it.severity == Severity.ERROR
            }.map {
                "${file.relativeTo(CORPUS).invariantSeparatorsPathString}:${it.lineNumber}: ${it.message}"
            }
        }

        assertThat(files).isNotEmpty
        assertThat(errors).isEmpty()
    }

    companion object {
        private const val GAMMA_EXTENSION = "gcd"
        private val CORPUS: Path = Path("..", "models", "corpus")
    }
}
