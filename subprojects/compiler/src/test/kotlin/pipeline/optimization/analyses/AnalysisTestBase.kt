/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceTree
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.verifyInjectedDependenciesAreBound
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files

/**
 * Base class for direct analysis tests.
 *
 * A fixture is an inlined-oxsts snippet (same form as [hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.PassTestBase])
 * that references `semantifyr::Anything` and declares only top-level variables
 * (no root feature, no nested instances). The base parses the snippet, wraps
 * it in a minimal [EvaluableCompilationContext] with a single-root instance
 * tree, then runs the requested [Analysis] against it.
 *
 * Tests inspect the analysis result directly rather than observing a pass's
 * output, so mismatches pinpoint the analysis itself instead of surfacing
 * indirectly through a downstream transformation.
 */
@InjectWithOxsts
abstract class AnalysisTestBase {

    @Inject
    protected lateinit var parseHelper: InlinedOxstsParseHelper

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
        val inlined = parseHelper.parse(source.trimIndent())
        val classDeclaration = inlined.classDeclaration
            ?: error("InlinedOxsts fixture must reference a class declaration (use 'inlined oxsts of semantifyr::Anything')")
        val tree = SingleRootInstanceTree(classDeclaration)
        val context = CreatedCompilationContext(inlined).instantiated(tree)
        return CompiledFixture(context, inlined)
    }

    /**
     * Parse [source], construct the compilation context, and run the analysis
     * [analysisClass] on it. Returns both the analysis result and the parsed
     * inlined oxsts so tests can look up specific IR elements.
     */
    protected fun <T : Any, A : Analysis<T>> runAnalysis(
        source: String,
        analysisClass: Class<A>,
    ): AnalysisRun<T> {
        val fixture = compile(source)

        val child = injector.createChildInjector(
            CompilationModule(
                ArtifactConfig.none(Files.createTempDirectory("analysis-test-")),
                OptimizationConfig.ALL,
            ),
        )
        val analysis = child.getInstance(analysisClass)
        val result = analysis.compute(fixture.context)
        return AnalysisRun(result, fixture.inlinedOxsts)
    }

    protected data class AnalysisRun<T : Any>(
        val result: T,
        val inlinedOxsts: InlinedOxsts,
    )

    private class SingleRootInstanceTree(domain: DomainDeclaration) : InstanceTree {
        override val rootInstance: Instance = Instance(domain, parent = null, tree = this)
    }
}
