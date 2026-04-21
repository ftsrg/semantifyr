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

/**
 * The main Compiler entrypoint that runs the whole Semantifyr compilation pipeline on the input models.
 */
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

    /**
     * Compiles a top-level [ClassDeclaration] through the full pipeline
     * (instantiation -> inlining -> deflation, with optimizer passes at the
     * inlined and flattened phases).
     *
     * Each call creates a fresh [CompilationPipeline] in a child injector, so
     * per-compilation services (artifact manager, instance tree, optimizer
     * state) start clean. Safe to call concurrently from different threads.
     *
     * Use this entrypoint for normal verification: the caller has a loaded
     * model and wants the deflated IR to feed a backend.
     */
    fun compile(classDeclaration: ClassDeclaration): FlattenedCompilationContext {
        logger.info { "Compiling class '${classDeclaration.name}'" }
        return freshPipeline().compileDeflated(classDeclaration)
    }

    /**
     * Compiles an already-inlined [InlinedOxsts] through the pipeline. Used
     * primarily to replay witness models: a counterexample captured from a
     * backend can be re-compiled and re-verified.
     *
     * The pipeline still runs instantiation, inlining, and deflation on the
     * input. "Already inlined" refers to its serialized form, not skipping
     * any compilation stage.
     */
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
