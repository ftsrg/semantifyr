/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceTree
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.utils.normalizedFixtureSource
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.compiler.scopes.withCompilationScopeBlocking
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.serializer.ISerializer
import java.nio.file.Files

@InjectWithOxsts
abstract class PassTestBase {
    @Inject
    protected lateinit var parseHelper: InlinedOxstsParseHelper

    @Inject
    protected lateinit var serializer: ISerializer

    @Inject
    protected lateinit var injector: Injector

    protected data class CompiledFixture(
        val context: EvaluableCompilationContext,
        val inlinedOxsts: InlinedOxsts,
    )

    protected fun compile(source: String): CompiledFixture {
        val inlined = parseHelper.parse(source.normalizedFixtureSource())
        val classDeclaration = inlined.classDeclaration ?: error("InlinedOxsts fixture must reference a class declaration (use 'inlined oxsts of semantifyr::Anything')")
        val tree = SingleRootInstanceTree(classDeclaration)
        val context = CreatedCompilationContext(inlined).instantiated(tree)
        return CompiledFixture(context, inlined)
    }

    protected fun assertPassTransforms(
        source: String,
        expectedSource: String,
        analysisClasses: List<Class<out Analysis<*>>> = emptyList(),
        buildPass: (Injector) -> Pass<EvaluableCompilationContext>,
    ) {
        val actual = compile(source)
        val expected = compile(expectedSource)

        val child = injector.createChildInjector(
            CompilationModule(
                ArtifactConfig.NONE,
                OptimizationConfig.ALL,
            ),
        )

        val request = CompilationRequest(
            inlinedOxsts = actual.inlinedOxsts,
            outputDirectory = Files.createTempDirectory("pass-test-"),
        )
        val passClassName = withCompilationScopeBlocking(request) {
            val analyses = analysisClasses.map {
                child.getInstance(it)
            }
            val analysisManager = AnalysisManager(analyses)
            val pass = buildPass(child)
            pass.run(actual.context, analysisManager)
            pass::class.simpleName
        }

        val actualText = serializer.serializeFormatted(actual.inlinedOxsts)
        val expectedText = serializer.serializeFormatted(expected.inlinedOxsts)
        assertThat(actualText)
            .describedAs("Pass $passClassName should have rewritten the input to the expected form")
            .isEqualTo(expectedText)
    }

    private class SingleRootInstanceTree(
        domain: DomainDeclaration,
    ) : InstanceTree {
        override val rootInstance: Instance = Instance(domain, parent = null, tree = this)
    }
}
