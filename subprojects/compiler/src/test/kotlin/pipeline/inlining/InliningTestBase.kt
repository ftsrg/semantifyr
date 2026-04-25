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
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.InlinedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.OxstsInstantiator
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.utils.normalizedFixtureSource
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.compiler.scopes.withCompilationScopeBlocking
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.serializer.ISerializer
import java.nio.file.Files

@InjectWithOxsts
abstract class InliningTestBase {
    @Inject
    protected lateinit var parseHelper: InlinedOxstsParseHelper

    @Inject
    protected lateinit var packageParseHelper: OxstsPackageParseHelper

    @Inject
    protected lateinit var injector: Injector

    @Inject
    protected lateinit var serializer: ISerializer

    protected data class Prepared(
        val inlinedOxsts: InlinedOxsts,
        val context: InstantiatedCompilationContext,
        val compilationInjector: Injector,
    ) {
        val rootInstance: Instance
            get() = context.rootInstance
    }

    protected fun <T> prepare(
        source: String,
        block: (Prepared) -> T,
    ): T {
        val inlined = parseHelper.parse(source.normalizedFixtureSource())
        val compilationInjector = newCompilationInjector()
        return withCompilationScopeBlocking(inlined) {
            val instantiator = compilationInjector.getInstance(OxstsInstantiator::class.java)
            val instantiated = instantiator.instantiate(CreatedCompilationContext(inlined))
            block(Prepared(instantiated.inlinedOxsts, instantiated, compilationInjector))
        }
    }

    protected fun <T> prepare(
        className: String,
        source: String,
        block: (Prepared) -> T,
    ): T {
        val classDecl = parseClassFromPackage(className, source)
        val compilationInjector = newCompilationInjector()
        val created = compilationInjector.getInstance(InlinedOxstsModelCreator::class.java).create(classDecl)
        return withCompilationScopeBlocking(created.inlinedOxsts) {
            val instantiator = compilationInjector.getInstance(OxstsInstantiator::class.java)
            val instantiated = instantiator.instantiate(created)
            block(Prepared(instantiated.inlinedOxsts, instantiated, compilationInjector))
        }
    }

    protected fun inlineAll(prepared: Prepared): InlinedCompilationContext {
        val inliner = prepared.compilationInjector.getInstance(OxstsInliner::class.java)
        return inliner.inline(prepared.context)
    }

    protected fun assertSerializedModelEquals(
        actualInlinedOxsts: InlinedOxsts,
        expectedSource: String,
    ) {
        val expectedInlined = parseHelper.parse(expectedSource.normalizedFixtureSource())
        val expectedText = serializer.serializeFormatted(expectedInlined)
        val actualText = serializer.serializeFormatted(actualInlinedOxsts)
        assertThat(actualText)
            .`as`("inlining result should match expected model")
            .isEqualTo(expectedText)
    }

    private fun parseClassFromPackage(
        className: String,
        source: String,
    ): ClassDeclaration {
        val parsed = packageParseHelper.parse(source.normalizedFixtureSource())
        val errors = parsed.resourceErrors
        if (errors.isNotEmpty()) {
            val formatted = errors.joinToString("\n") {
                "  ${it.location ?: "<unknown>"}:${it.line}:${it.column}: ${it.message}"
            }
            error("Test fixture failed to parse:\n$formatted")
        }
        return EcoreUtil2
            .eAllOfType(parsed.oxstsPackage, ClassDeclaration::class.java)
            .singleOrNull { it.name == className }
            ?: error("Class '$className' not found in the test model")
    }

    private fun newCompilationInjector(): Injector = injector.createChildInjector(
        CompilationModule(
            ArtifactConfig.none(Files.createTempDirectory("inlining-test-")),
            OptimizationConfig.ALL,
        ),
    )
}
