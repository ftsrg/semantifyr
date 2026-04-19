/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.AbstractModule
import com.google.inject.assistedinject.FactoryModuleBuilder
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.StaticElementValueEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.StaticExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.ExpressionCallInliner
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.OperationCallInliner
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig

class CompilationModule(
    private val artifacts: ArtifactConfig,
    private val optimization: OptimizationConfig,
) : AbstractModule() {

    override fun configure() {
        install(FactoryModuleBuilder().build(StaticExpressionEvaluator.Factory::class.java))
        install(FactoryModuleBuilder().build(MetaStaticExpressionEvaluator.Factory::class.java))
        install(FactoryModuleBuilder().build(StaticElementValueEvaluator.Factory::class.java))
        install(FactoryModuleBuilder().build(ExpressionCallInliner.Factory::class.java))
        install(FactoryModuleBuilder().build(OperationCallInliner.Factory::class.java))

        bind(ArtifactConfig::class.java).toInstance(artifacts)
        bind(OptimizationConfig::class.java).toInstance(optimization)

        super.configure()
    }
}
