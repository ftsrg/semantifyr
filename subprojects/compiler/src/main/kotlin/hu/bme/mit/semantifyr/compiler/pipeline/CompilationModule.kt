/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.AbstractModule
import com.google.inject.Provider
import com.google.inject.assistedinject.FactoryModuleBuilder
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeElementValueEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.ExpressionCallInliner
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.OperationCallInliner
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.scopes.CompilationScope
import hu.bme.mit.semantifyr.compiler.scopes.CompilationScoped
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

/**
 * Shape-level compilation module installed once per [SemantifyrCompiler]. Binds the
 * shared compiler config, wires the compilation scope and its seed slots, and installs
 * the AssistedInject factories. Per-compilation values (notably [InlinedOxsts]) are
 * supplied by seeding the compilation scope at entry, not by constructing this module
 * per call.
 */
class CompilationModule(
    private val artifacts: ArtifactConfig,
    private val optimization: OptimizationConfig,
) : AbstractModule() {
    override fun configure() {
        bind(ArtifactConfig::class.java).toInstance(artifacts)
        bind(OptimizationConfig::class.java).toInstance(optimization)

        bindScope(CompilationScoped::class.java, CompilationScope)

        bind(InlinedOxsts::class.java)
            .toProvider(Provider {
                error("InlinedOxsts must be seeded into the compilation scope; call withCompilationScope with a Seed that includes it.")
            })
            .`in`(CompilationScope)

        install(FactoryModuleBuilder().build(CompileTimeExpressionEvaluator.Factory::class.java))
        install(FactoryModuleBuilder().build(MetaCompileTimeExpressionEvaluator.Factory::class.java))
        install(FactoryModuleBuilder().build(CompileTimeElementValueEvaluator.Factory::class.java))
        install(FactoryModuleBuilder().build(ExpressionCallInliner.Factory::class.java))
        install(FactoryModuleBuilder().build(OperationCallInliner.Factory::class.java))
    }
}
