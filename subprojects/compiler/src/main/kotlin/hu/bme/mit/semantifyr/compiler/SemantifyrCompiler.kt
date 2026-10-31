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
import hu.bme.mit.semantifyr.compiler.pipeline.ModelSizeCounter
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.scopes.withCompilationScopeBlocking
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import java.nio.file.Path
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.TimeSource.Monotonic.markNow

class SemantifyrCompiler(
    injector: Injector,
    artifactConfig: ArtifactConfig,
    optimizationConfig: OptimizationConfig = OptimizationConfig.ALL,
) {

    private val logger by loggerFactory()

    private val sharedInjector = injector.createChildInjector(
        CompilationModule(artifactConfig, optimizationConfig),
    )

    private val modelSizeCounter = sharedInjector.getInstance(ModelSizeCounter::class.java)

    fun compile(classDeclaration: ClassDeclaration, outputDirectory: Path): FlattenedCompilationContext {
        logger.info { "Compiling class '${classDeclaration.name}' into ${outputDirectory.toAbsolutePath()}" }
        val inputSize = modelSizeCounter.count(classDeclaration.eResource())
        val createMark = markNow()
        val inlinedOxsts = sharedInjector.getInstance(InlinedOxstsModelCreator::class.java)
            .create(classDeclaration, outputDirectory)
            .inlinedOxsts
        return compile(inlinedOxsts, outputDirectory, inputSize, createMark)
    }

    fun compile(inlinedOxsts: InlinedOxsts, outputDirectory: Path): FlattenedCompilationContext {
        logger.info { "Compiling inlined oxsts of '${inlinedOxsts.classDeclaration.name}' into ${outputDirectory.toAbsolutePath()}" }
        val inputSize = modelSizeCounter.count(inlinedOxsts)
        return compile(inlinedOxsts, outputDirectory, inputSize, markNow())
    }

    private fun compile(
        inlinedOxsts: InlinedOxsts,
        outputDirectory: Path,
        inputSize: ModelSize,
        createMark: ValueTimeMark,
    ): FlattenedCompilationContext {
        val request = CompilationRequest(inlinedOxsts = inlinedOxsts, outputDirectory = outputDirectory)
        return withCompilationScopeBlocking(request) {
            val pipeline = sharedInjector.getInstance(CompilationPipeline::class.java)
            pipeline.compileFlattened(inlinedOxsts, inputSize, createMark)
        }
    }

}
