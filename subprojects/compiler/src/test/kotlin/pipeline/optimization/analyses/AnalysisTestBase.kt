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
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceTree
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.verifyInjectedDependenciesAreBound
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files

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

    protected data class Fixture(
        val inlinedOxsts: InlinedOxsts,
        val evaluator: MetaStaticExpressionEvaluator,
        val child: Injector,
    )

    protected fun compile(source: String): Fixture {
        val inlined = parseHelper.parse(source.trimIndent())
        val classDeclaration = inlined.classDeclaration
            ?: error("InlinedOxsts fixture must reference a class declaration (use 'inlined oxsts of semantifyr::Anything')")
        val tree = SingleRootInstanceTree(classDeclaration)
        val context = CreatedCompilationContext(inlined).instantiated(tree)
        val child = injector.createChildInjector(
            CompilationModule(
                ArtifactConfig.none(Files.createTempDirectory("analysis-test-")),
                OptimizationConfig.ALL,
            ),
        )
        val evaluator = child.getInstance(MetaStaticExpressionEvaluatorProvider::class.java)
            .getEvaluator(context.rootInstance)
        return Fixture(inlined, evaluator, child)
    }

    protected fun runLiveness(source: String): Pair<InlinedOxsts, LivenessInfo> {
        val (inlined, evaluator) = compile(source)
        return inlined to LivenessComputation(inlined, evaluator).compute()
    }

    protected fun runConstantValue(source: String): Pair<InlinedOxsts, ConstantValueInfo> {
        val (inlined, evaluator, child) = compile(source)
        val result = ConstantValueComputation(
            inlined,
            evaluator,
            child.getInstance(ConstantExpressionEvaluatorProvider::class.java),
        ).compute()
        return inlined to result
    }

    protected fun runReachingDefinitions(source: String): Pair<InlinedOxsts, ReachingDefinitionsInfo> {
        val (inlined, evaluator) = compile(source)
        return inlined to ReachingDefinitionsComputation(inlined, evaluator).compute()
    }

    protected fun runConeOfInfluence(source: String): Pair<InlinedOxsts, ConeOfInfluenceInfo> {
        val (inlined, evaluator) = compile(source)
        return inlined to ConeOfInfluenceComputation(inlined, evaluator).compute()
    }

    private class SingleRootInstanceTree(domain: DomainDeclaration) : InstanceTree {
        override val rootInstance: Instance = Instance(domain, parent = null, tree = this)
    }
}
