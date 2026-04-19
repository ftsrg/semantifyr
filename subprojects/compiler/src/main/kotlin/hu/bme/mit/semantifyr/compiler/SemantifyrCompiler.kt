/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationPipeline
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig

/**
 * The main Compiler entrypoint that runs the whole Semantifyr compilation pipeline on the input models.
 * The pipeline is instantiated in a fresh [com.google.inject.Injector] on each call.
 */
class SemantifyrCompiler @JvmOverloads constructor(
    private val artifactConfig: ArtifactConfig,
    private val optimizationConfig: OptimizationConfig = OptimizationConfig.DEFAULT,
) : AutoCloseable {

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()

    private val logger by loggerFactory()

    fun compile(classDeclaration: ClassDeclaration): FlattenedCompilationContext {
        logger.debug { "Compiling class '${classDeclaration.name}'" }
        return freshPipeline().compileDeflated(classDeclaration)
    }

    fun compile(inlinedOxsts: InlinedOxsts): FlattenedCompilationContext {
        logger.debug { "Compiling inlined oxsts of '${inlinedOxsts.classDeclaration.name}'" }
        return freshPipeline().compileDeflated(inlinedOxsts)
    }

    override fun close() {

    }

    private fun freshPipeline(): CompilationPipeline {
        return injector.createChildInjector(CompilationModule(artifactConfig, optimizationConfig))
            .getInstance(CompilationPipeline::class.java)
    }

}
