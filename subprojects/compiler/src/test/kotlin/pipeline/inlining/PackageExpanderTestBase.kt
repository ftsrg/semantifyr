/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.InlinedOxstsModelCreator
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.InlinedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.OxstsInstantiator
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.io.StringWriter
import java.nio.file.Files

/**
 * Base for expander tests that need a full OXSTS package, with class
 * hierarchies, features, inheritance and redefinition. Parses the source,
 * wraps the named class in an [InlinedOxsts] via [InlinedOxstsModelCreator],
 * runs [OxstsInstantiator] to produce an instance tree, and exposes the
 * compilation-scoped injector so tests can pull expanders and drive them
 * against single IR nodes.
 *
 * Use [prepare] with the class name to compile; tests then locate an IR
 * node (first InlineCall, named transition body, etc.), run the expander,
 * and compare the serialized post-inflation model against an expected
 * serialization.
 */
@InjectWithOxsts
abstract class PackageExpanderTestBase {

    @Inject
    protected lateinit var packageParseHelper: OxstsPackageParseHelper

    @Inject
    protected lateinit var injector: Injector

    @Inject
    protected lateinit var serializer: ISerializer

    protected fun prepare(className: String, source: String): Prepared {
        val parsed = packageParseHelper.parse(source.trimIndent())
        val resourceErrors = parsed.resourceErrors
        if (resourceErrors.isNotEmpty()) {
            val formatted = resourceErrors.joinToString("\n") { d ->
                "  ${d.location ?: "<unknown>"}:${d.line}:${d.column}: ${d.message}"
            }
            error("Test fixture failed to parse:\n$formatted")
        }

        val classDecl = EcoreUtil2.eAllOfType(parsed.oxstsPackage, ClassDeclaration::class.java)
            .singleOrNull { it.name == className }
            ?: error("Class '$className' not found in the test model")

        val compilationInjector = injector.createChildInjector(
            CompilationModule(
                ArtifactConfig.none(Files.createTempDirectory("package-expander-test-")),
                OptimizationConfig.ALL,
            ),
        )

        val modelCreator = compilationInjector.getInstance(InlinedOxstsModelCreator::class.java)
        val instantiator = compilationInjector.getInstance(OxstsInstantiator::class.java)

        val created = modelCreator.create(classDecl)
        val instantiated = instantiator.instantiate(created)

        return Prepared(instantiated.inlinedOxsts, instantiated, compilationInjector)
    }

    protected data class Prepared(
        val inlinedOxsts: InlinedOxsts,
        val context: InstantiatedCompilationContext,
        val compilationInjector: Injector,
    ) {
        val rootInstance: Instance get() = context.rootInstance
    }

    /**
     * Run the inlining phase (operation + expression) on [prepared] and
     * return the resulting inlined context. Tests that care about the
     * final inlined shape - not one expander step in isolation - use
     * this and inspect `result.inlinedOxsts`.
     */
    protected fun inlineAll(prepared: Prepared): InlinedCompilationContext {
        val inliner = prepared.compilationInjector.getInstance(
            hu.bme.mit.semantifyr.compiler.pipeline.inlining.OxstsInliner::class.java,
        )
        return inliner.inline(prepared.context)
    }

    protected fun serializeNormalized(eObject: EObject): String {
        val writer = StringWriter()
        serializer.serialize(eObject, writer, SaveOptions.defaultOptions())
        return writer.toString().replace(WHITESPACE_RUN, " ").trim()
    }

    /**
     * Assert that a serialized IR subtree matches the given expected text
     * up to whitespace. Compares the raw serialized forms; useful when the
     * test just inspects one branch of the expanded operation.
     */
    protected fun assertSerializedEquals(expected: String, actual: EObject) {
        val actualText = serializeNormalized(actual)
        val expectedText = expected.trimIndent().replace(WHITESPACE_RUN, " ").trim()
        assertThat(actualText)
            .`as`("expander result should match expected text")
            .isEqualTo(expectedText)
    }

    private companion object {
        private val WHITESPACE_RUN = Regex("\\s+")
    }
}
