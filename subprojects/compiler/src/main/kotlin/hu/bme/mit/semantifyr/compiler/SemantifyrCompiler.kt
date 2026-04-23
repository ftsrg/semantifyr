/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationPipeline
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig

class SemantifyrCompiler(
    private val injector: Injector,
    private val artifactConfig: ArtifactConfig,
    private val optimizationConfig: OptimizationConfig = OptimizationConfig.DEFAULT,
) : AutoCloseable {

    @JvmOverloads
    constructor(
        artifactConfig: ArtifactConfig,
        optimizationConfig: OptimizationConfig = OptimizationConfig.DEFAULT,
    ) : this(OxstsStandaloneSetup().createInjectorAndDoEMFRegistration(), artifactConfig, optimizationConfig)

    private val logger by loggerFactory()

    init {
        logger.info { "Compiler artifacts will be written to: ${artifactConfig.outputDirectory.toAbsolutePath()}" }
    }

    fun compile(classDeclaration: ClassDeclaration): FlattenedCompilationContext {
        logger.info { "Compiling class '${classDeclaration.name}'" }
        return freshPipeline().compileDeflated(classDeclaration)
    }

    fun compile(inlinedOxsts: InlinedOxsts): FlattenedCompilationContext {
        logger.info { "Compiling inlined oxsts of '${inlinedOxsts.classDeclaration.name}'" }
        return freshPipeline().compileDeflated(inlinedOxsts)
    }

    override fun close() {

    }

    private fun freshPipeline(): CompilationPipeline {
        return injector.createChildInjector(CompilationModule(artifactConfig, optimizationConfig))
            .getInstance(CompilationPipeline::class.java)
    }

}
