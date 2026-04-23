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
import hu.bme.mit.semantifyr.compiler.pipeline.utils.normalizedFixtureSource
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.serializer.ISerializer
import java.nio.file.Files

@InjectWithOxsts
abstract class PackageExpanderTestBase {

    @Inject
    protected lateinit var packageParseHelper: OxstsPackageParseHelper

    @Inject
    protected lateinit var injector: Injector

    @Inject
    protected lateinit var serializer: ISerializer

    protected fun prepare(className: String, source: String): Prepared {
        val parsed = packageParseHelper.parse(source.normalizedFixtureSource())
        val resourceErrors = parsed.resourceErrors
        if (resourceErrors.isNotEmpty()) {
            val formatted = resourceErrors.joinToString("\n") {
                "  ${it.location ?: "<unknown>"}:${it.line}:${it.column}: ${it.message}"
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
        val inliner = prepared.compilationInjector.getInstance(OxstsInliner::class.java)
        return inliner.inline(prepared.context)
    }
}
