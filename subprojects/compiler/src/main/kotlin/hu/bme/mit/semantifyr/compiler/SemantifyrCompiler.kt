/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationPipeline
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import hu.bme.mit.semantifyr.compiler.pipeline.InlinedOxstsModelCreator
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.scopes.withCompilationScopeBlocking
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import java.nio.file.Path
import kotlin.time.measureTimedValue

class SemantifyrCompiler(
    injector: Injector,
    artifactConfig: ArtifactConfig,
    optimizationConfig: OptimizationConfig = OptimizationConfig.ALL,
) {

    private val logger by loggerFactory()

    private val sharedInjector = injector.createChildInjector(
        CompilationModule(artifactConfig, optimizationConfig),
    )

    fun compile(classDeclaration: ClassDeclaration, outputDirectory: Path): FlattenedCompilationContext {
        logger.info { "Compiling class '${classDeclaration.name}' into ${outputDirectory.toAbsolutePath()}" }
        val (inlinedOxsts, createDuration) = measureTimedValue {
            sharedInjector.getInstance(InlinedOxstsModelCreator::class.java)
                .create(classDeclaration, outputDirectory)
                .inlinedOxsts
        }
        val flattened = compile(inlinedOxsts, outputDirectory)
        return FlattenedCompilationContext(
            inlinedOxsts = flattened.inlinedOxsts,
            rootInstance = flattened.rootInstance,
            transitionCallTraces = flattened.transitionCallTraces,
            flatteningInfo = flattened.flatteningInfo,
            compilationMetrics = flattened.compilationMetrics.copy(createDuration = createDuration),
        )
    }

    fun compile(inlinedOxsts: InlinedOxsts, outputDirectory: Path): FlattenedCompilationContext {
        logger.info { "Compiling inlined oxsts of '${inlinedOxsts.classDeclaration.name}' into ${outputDirectory.toAbsolutePath()}" }
        val request = CompilationRequest(inlinedOxsts = inlinedOxsts, outputDirectory = outputDirectory)
        return withCompilationScopeBlocking(request) {
            val pipeline = sharedInjector.getInstance(CompilationPipeline::class.java)
            pipeline.compileFlattened(inlinedOxsts)
        }
    }

}
