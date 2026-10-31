/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.OxstsFlattener
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.OxstsInliner
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.OxstsInstantiator
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.TimeSource.Monotonic.markNow

class CompilationPipeline @Inject constructor(
    private val oxstsInstantiator: OxstsInstantiator,
    private val oxstsInliner: OxstsInliner,
    private val oxstsFlattener: OxstsFlattener,
    private val compilationArtifactManager: CompilationArtifactManager,
    private val modelSizeCounter: ModelSizeCounter,
) {

    private val logger by loggerFactory()

    // phases are timed back to back from createMark, so their sum is the unfolding time without size counting
    fun compileFlattened(
        inlinedOxsts: InlinedOxsts,
        inputSize: ModelSize,
        createMark: ValueTimeMark,
    ): FlattenedCompilationContext {
        val instantiationStart = markNow()
        val createDuration = instantiationStart - createMark
        logger.info { "Starting compilation of '${inlinedOxsts.classDeclaration.name}', created in $createDuration" }

        logger.info { "Phase: instantiation" }
        val instantiatedContext = oxstsInstantiator.instantiate(CreatedCompilationContext(inlinedOxsts))
        compilationArtifactManager.commitInstantiated()
        val inliningStart = markNow()
        val instantiationDuration = inliningStart - instantiationStart
        logger.info { "Phase: instantiation done in $instantiationDuration" }

        logger.info { "Phase: inlining" }
        val inlinedContext = oxstsInliner.inline(instantiatedContext)
        compilationArtifactManager.commitInlined()
        val inliningDuration = inliningStart.elapsedNow()
        logger.info { "Phase: inlining done in $inliningDuration" }

        val inlinedSize = modelSizeCounter.count(inlinedContext)

        val flatteningStart = markNow()
        logger.info { "Phase: flattening" }
        val flattenedContext = oxstsFlattener.flatten(inlinedContext)
        compilationArtifactManager.commitFlattened()
        val flatteningDuration = flatteningStart.elapsedNow()
        logger.info { "Phase: flattening done in $flatteningDuration" }

        val compilationMetrics = flattenedContext.compilationMetrics.copy(
            createDuration = createDuration,
            instantiationDuration = instantiationDuration,
            inliningDuration = inliningDuration,
            flatteningDuration = flatteningDuration,
            inputSize = inputSize,
            inlinedSize = inlinedSize,
            flattenedSize = modelSizeCounter.count(flattenedContext.inlinedOxsts),
        )
        logger.info { "Compilation finished in ${compilationMetrics.unfoldingDuration}" }

        return FlattenedCompilationContext(
            inlinedOxsts = flattenedContext.inlinedOxsts,
            rootInstance = flattenedContext.rootInstance,
            transitionCallTraces = flattenedContext.transitionCallTraces,
            flatteningInfo = flattenedContext.flatteningInfo,
            compilationMetrics = compilationMetrics,
        )
    }

}
