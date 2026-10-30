/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceTree
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.scopes.withCompilationScopeBlocking
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import java.nio.file.Files

@InjectWithOxsts
abstract class AnalysisTestBase {
    @Inject
    protected lateinit var parseHelper: InlinedOxstsParseHelper

    @Inject
    protected lateinit var injector: Injector

    protected data class Fixture(
        val inlinedOxsts: InlinedOxsts,
        val evaluator: MetaCompileTimeExpressionEvaluator,
        val child: Injector,
    )

    protected fun <T> withCompiled(
        source: String,
        block: (Fixture) -> T,
    ): T {
        val inlined = parseHelper.parse(source.trimIndent())
        val classDeclaration = inlined.classDeclaration ?: error("InlinedOxsts fixture must reference a class declaration (use 'inlined oxsts of semantifyr::Anything')")
        val tree = SingleRootInstanceTree(classDeclaration)
        val context = CreatedCompilationContext(inlined).instantiated(tree)
        val child = injector.createChildInjector(
            CompilationModule(
                ArtifactConfig.NONE,
                OptimizationConfig.ALL,
            ),
        )

        val request = CompilationRequest(
            inlinedOxsts = inlined,
            outputDirectory = Files.createTempDirectory("analysis-test-"),
        )
        return withCompilationScopeBlocking(request) {
            val evaluator = child
                .getInstance(MetaCompileTimeExpressionEvaluatorProvider::class.java)
                .getEvaluator(context.rootInstance)
            block(Fixture(inlined, evaluator, child))
        }
    }

    protected fun runLiveness(source: String): Pair<InlinedOxsts, LivenessInfo> {
        return withCompiled(source) { (inlined, evaluator) ->
            inlined to LivenessComputation(inlined, evaluator).compute()
        }
    }

    protected fun runConstantValue(source: String): Pair<InlinedOxsts, ConstantValueInfo> {
        return withCompiled(source) { (inlined, evaluator, child) ->
            val result = ConstantValueComputation(
                inlined,
                evaluator,
                child.getInstance(ConstantExpressionEvaluatorProvider::class.java),
            ).compute()
            inlined to result
        }
    }

    protected fun runReachingDefinitions(source: String): Pair<InlinedOxsts, ReachingDefinitionsInfo> {
        return withCompiled(source) { (inlined, evaluator) ->
            inlined to ReachingDefinitionsComputation(inlined, evaluator).compute()
        }
    }

    protected fun runConeOfInfluence(source: String): Pair<InlinedOxsts, ConeOfInfluenceInfo> {
        return withCompiled(source) { (inlined, evaluator) ->
            inlined to ConeOfInfluenceComputation(inlined, evaluator).compute()
        }
    }

    private class SingleRootInstanceTree(
        domain: DomainDeclaration,
    ) : InstanceTree {
        override val rootInstance: Instance = Instance(domain, parent = null, tree = this)
    }
}
