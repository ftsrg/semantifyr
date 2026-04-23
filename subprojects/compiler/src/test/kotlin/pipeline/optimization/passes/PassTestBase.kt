/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceTree
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.verifyInjectedDependenciesAreBound
import hu.bme.mit.semantifyr.compiler.pipeline.utils.normalizedFixtureSource
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.serializer.ISerializer
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files

/**
 * Base class for analysis-driven pass tests.
 *
 * Fixtures are inlined-oxsts snippets in the same form as [PatternTestBase] uses.
 * The pass runs against an [EvaluableCompilationContext] that wraps the
 * parsed [InlinedOxsts] with a single-root [InstanceTree] - tests declare only
 * top-level variables, so the root instance has no children and the
 * evaluator-backed analyses resolve references directly.
 *
 * Comparison is by formatted serialization, with actual and expected going
 * through the same whitespace-collapsed parse so the formatter picks its
 * minimum hidden-token ranges and the two sides match canonically.
 */
@InjectWithOxsts
abstract class PassTestBase {

    @Inject
    protected lateinit var parseHelper: InlinedOxstsParseHelper

    @Inject
    protected lateinit var serializer: ISerializer

    @Inject
    protected lateinit var injector: Injector

    @BeforeEach
    fun verifyInjectedDependencies() {
        verifyInjectedDependenciesAreBound(this)
    }

    protected data class CompiledFixture(
        val context: EvaluableCompilationContext,
        val inlinedOxsts: InlinedOxsts,
    )

    protected fun compile(source: String): CompiledFixture {
        val inlined = parseHelper.parse(source.normalizedFixtureSource())
        val classDeclaration = inlined.classDeclaration
            ?: error("InlinedOxsts fixture must reference a class declaration (use 'inlined oxsts of semantifyr::Anything')")
        val tree = SingleRootInstanceTree(classDeclaration)
        val context = CreatedCompilationContext(inlined).instantiated(tree)
        return CompiledFixture(context, inlined)
    }

    /**
     * Build a [Pass] from a fresh per-test child injector with [CompilationModule]
     * installed (assisted-inject factories for evaluators, plus ArtifactConfig and
     * OptimizationConfig bindings) and run it against the fixture. The comparison
     * is against [expectedSource] compiled through the same path.
     */
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
                ArtifactConfig.none(Files.createTempDirectory("pass-test-")),
                OptimizationConfig.ALL,
            ),
        )

        val analyses = analysisClasses.map {
            child.getInstance(it)
        }
        val analysisManager = AnalysisManager(analyses)
        val pass = buildPass(child)
        pass.run(actual.context, analysisManager)

        val actualText = serializer.serializeFormatted(actual.inlinedOxsts)
        val expectedText = serializer.serializeFormatted(expected.inlinedOxsts)
        assertThat(actualText)
            .describedAs("Pass ${pass::class.simpleName} should have rewritten the input to the expected form")
            .isEqualTo(expectedText)
    }

    /**
     * [InstanceTree] with a single root instance - enough for tests whose
     * fixtures use only top-level variables on the inlinedOxsts (no features,
     * no nested instances). The evaluator only needs to resolve direct
     * variable references; it never has to navigate through child instances.
     */
    private class SingleRootInstanceTree(
        domain: DomainDeclaration,
    ) : InstanceTree {
        override val rootInstance: Instance = Instance(domain, parent = null, tree = this)
    }
}
