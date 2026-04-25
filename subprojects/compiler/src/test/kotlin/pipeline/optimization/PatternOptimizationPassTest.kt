/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceTree
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

@InjectWithOxsts
class PatternOptimizationPassTest {
    @Inject
    private lateinit var parseHelper: InlinedOxstsParseHelper

    @Inject
    private lateinit var injector: Injector

    @BeforeEach
    fun verifyInjectedDependencies() {
        verifyInjectedDependenciesAreBound(this)
    }

    @Test
    fun `returns Unchanged without invoking patterns when every category is disabled`() {
        val pattern = CountingPattern(alwaysFires = true)
        val pass = pass(
            config = OptimizationConfig.NONE,
            categories = listOf(OptimizationCategory.ExpressionSimplification),
            pattern = pattern,
        )

        val result = pass.run(context(), AnalysisManager(emptyList()))

        assertThat(result).isEqualTo(PassResult.Unchanged)
        assertThat(pattern.invocations)
            .`as`("patterns must not run when the pass is gated off")
            .isZero
    }

    @Test
    fun `returns Unchanged when patterns match nothing`() {
        val pattern = CountingPattern(alwaysFires = false)
        val pass = pass(pattern = pattern)

        val result = pass.run(context(), AnalysisManager(emptyList()))

        assertThat(result).isEqualTo(PassResult.Unchanged)
        assertThat(pattern.invocations)
            .`as`("non-matching patterns still get dispatched over the worklist")
            .isPositive
    }

    @Test
    fun `returns Changed when at least one pattern application fires`() {
        // The pattern fires once on first invocation and then signals "done",
        // so the worklist exits after a single application.
        val pattern = CountingPattern(alwaysFires = false, firesOnInvocation = 1)
        val pass = pass(pattern = pattern)

        val result = pass.run(context(), AnalysisManager(emptyList()))

        assertThat(result).isInstanceOf(PassResult.Changed::class.java)
    }

    @Test
    fun `multi-category gate runs the pass when any one category is enabled`() {
        val pattern = CountingPattern(alwaysFires = false)
        val pass = pass(
            config = OptimizationConfig(enabled = setOf(OptimizationCategory.ConstantFolding)),
            categories = listOf(
                OptimizationCategory.ExpressionSimplification,
                OptimizationCategory.ConstantFolding,
            ),
            pattern = pattern,
        )

        pass.run(context(), AnalysisManager(emptyList()))

        assertThat(pattern.invocations)
            .`as`("enabling any one listed category must unlock the pass")
            .isPositive
    }

    private fun pass(
        config: OptimizationConfig = OptimizationConfig.ALL,
        categories: List<OptimizationCategory> = listOf(OptimizationCategory.ExpressionSimplification),
        pattern: OptimizationPattern,
    ): PatternOptimizationPass {
        return object : PatternOptimizationPass(
            config = config,
            categories = categories,
            compilationPass = CompilationPass.ExpressionSimplification,
            patterns = listOf(pattern),
            artifactManager = mock<CompilationArtifactManager>(),
        ) {}
    }

    private fun context(): EvaluableCompilationContext {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { }
                tran { a := 1 }
                prop { AG (a == 1) }
            """.trimIndent(),
        )
        val classDeclaration = inlined.classDeclaration
            ?: error("fixture must reference a class declaration")
        val tree = SingleRootInstanceTree(classDeclaration)
        return CreatedCompilationContext(inlined).instantiated(tree)
    }

    private class CountingPattern(
        private val alwaysFires: Boolean,
        private val firesOnInvocation: Int = -1,
    ) : OptimizationPattern {
        var invocations: Int = 0
            private set

        override fun tryApply(
            element: EObject,
            worklist: Worklist<EObject>,
        ): Boolean {
            invocations++
            if (alwaysFires) {
                return false // never loops forever; pattern claims "not applicable this element"
            }
            return invocations == firesOnInvocation
        }
    }

    private class SingleRootInstanceTree(
        domain: DomainDeclaration,
    ) : InstanceTree {
        override val rootInstance: Instance = Instance(domain, parent = null, tree = this)
    }
}
