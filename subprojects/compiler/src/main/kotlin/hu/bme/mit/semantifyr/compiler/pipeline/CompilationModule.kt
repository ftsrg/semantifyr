/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.AbstractModule
import com.google.inject.assistedinject.FactoryModuleBuilder
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeElementValueEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.ExpressionCallInliner
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.OperationCallInliner
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

/**
 * Shared compiler configuration module.
 */
class CompilationConfigModule(
    private val artifacts: ArtifactConfig,
    private val optimization: OptimizationConfig,
) : AbstractModule() {
    override fun configure() {
        bind(ArtifactConfig::class.java).toInstance(artifacts)
        bind(OptimizationConfig::class.java).toInstance(optimization)
    }
}

/**
 * Single use compilation module.
 */
class CompilationModule(
    private val inlinedOxsts: InlinedOxsts,
) : AbstractModule() {
    override fun configure() {
        install(FactoryModuleBuilder().build(CompileTimeExpressionEvaluator.Factory::class.java))
        install(FactoryModuleBuilder().build(MetaCompileTimeExpressionEvaluator.Factory::class.java))
        install(FactoryModuleBuilder().build(CompileTimeElementValueEvaluator.Factory::class.java))
        install(FactoryModuleBuilder().build(ExpressionCallInliner.Factory::class.java))
        install(FactoryModuleBuilder().build(OperationCallInliner.Factory::class.java))

        bind(InlinedOxsts::class.java).toInstance(inlinedOxsts)
    }
}
