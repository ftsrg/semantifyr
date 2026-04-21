/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.OxstsInstantiator
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.io.StringWriter
import java.nio.file.Files

/**
 * Base for single-step expander tests. Mirrors the shape of
 * [hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PatternTestBase]:
 * each test writes an inlined-oxsts input, the framework picks one IR node
 * (via a locate lambda), the expander under test runs on it, the result is
 * substituted in place, and the whole inlined-oxsts is compared to an
 * expected serialization (whitespace-normalized).
 *
 * The input is parsed as an [InlinedOxsts] directly (no package wrapping)
 * so tests can keep the source minimal. Instantiation runs against
 * `semantifyr::Anything` (or any referenced class) to produce the root
 * [Instance] the expander needs.
 */
@InjectWithOxsts
abstract class ExpanderTestBase {

    @Inject
    protected lateinit var parseHelper: InlinedOxstsParseHelper

    @Inject
    protected lateinit var injector: Injector

    @Inject
    protected lateinit var serializer: ISerializer

    /**
     * Parse [source], instantiate the model, and return both the parsed IR
     * and the child injector that subclasses can pull expanders from.
     */
    protected fun prepare(source: String): Prepared {
        val inlined = parseHelper.parse(source.trimIndent())

        val compilationInjector = injector.createChildInjector(
            CompilationModule(
                ArtifactConfig.none(Files.createTempDirectory("expander-test-")),
                OptimizationConfig.ALL,
            ),
        )
        val instantiator = compilationInjector.getInstance(OxstsInstantiator::class.java)
        val context = instantiator.instantiate(CreatedCompilationContext(inlined))

        return Prepared(inlined, context, compilationInjector)
    }

    protected data class Prepared(
        val inlinedOxsts: InlinedOxsts,
        val context: InstantiatedCompilationContext,
        val compilationInjector: Injector,
    ) {
        val rootInstance: Instance get() = context.rootInstance
    }

    /**
     * Serialize an EObject to a whitespace-normalized string for comparison.
     */
    protected fun serializeNormalized(eObject: EObject): String {
        val writer = StringWriter()
        serializer.serialize(eObject, writer, SaveOptions.defaultOptions())
        return writer.toString().replace(WHITESPACE_RUN, " ").trim()
    }

    /**
     * Assert that the [actualInlinedOxsts] (after an in-place expansion)
     * serializes identically to a fresh parse of [expectedSource].
     */
    protected fun assertSerializedModelEquals(actualInlinedOxsts: InlinedOxsts, expectedSource: String) {
        val expectedInlined = parseHelper.parse(expectedSource.trimIndent())
        val actualText = serializeNormalized(actualInlinedOxsts)
        val expectedText = serializeNormalized(expectedInlined)
        assertThat(actualText)
            .`as`("expander result should match expected model")
            .isEqualTo(expectedText)
    }

    private companion object {
        private val WHITESPACE_RUN = Regex("\\s+")
    }
}
