/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationPipeline
import hu.bme.mit.semantifyr.compiler.pipeline.InlinedOxstsModelCreator
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.scopes.withCompilationScopeBlocking
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

class SemantifyrCompiler(
    injector: Injector,
    private val artifactConfig: ArtifactConfig,
    optimizationConfig: OptimizationConfig = OptimizationConfig.DEFAULT,
) : AutoCloseable {

    @JvmOverloads
    constructor(
        artifactConfig: ArtifactConfig,
        optimizationConfig: OptimizationConfig = OptimizationConfig.DEFAULT,
    ) : this(OxstsStandaloneSetup().createInjectorAndDoEMFRegistration(), artifactConfig, optimizationConfig)

    private val logger by loggerFactory()

    private val sharedInjector = injector.createChildInjector(
        CompilationModule(artifactConfig, optimizationConfig),
    )

    init {
        logger.info { "Compiler artifacts will be written to: ${artifactConfig.outputDirectory.toAbsolutePath()}" }
    }

    fun compile(classDeclaration: ClassDeclaration): FlattenedCompilationContext {
        logger.info { "Compiling class '${classDeclaration.name}'" }
        val inlinedOxsts = sharedInjector
            .getInstance(InlinedOxstsModelCreator::class.java)
            .create(classDeclaration)
            .inlinedOxsts
        return compile(inlinedOxsts)
    }

    fun compile(inlinedOxsts: InlinedOxsts): FlattenedCompilationContext {
        logger.info { "Compiling inlined oxsts of '${inlinedOxsts.classDeclaration.name}'" }
        return withCompilationScopeBlocking(inlinedOxsts) {
            val pipeline = sharedInjector.getInstance(CompilationPipeline::class.java)
            pipeline.compileFlattened(inlinedOxsts)
        }
    }

    override fun close() {

    }

}
