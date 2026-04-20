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
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.OxstsFlattener
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.OxstsInliner
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.OxstsInstantiator
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import kotlin.time.TimeSource.Monotonic.markNow

class CompilationPipeline @Inject constructor(
    private val inlinedOxstsModelCreator: InlinedOxstsModelCreator,
    private val oxstsInstantiator: OxstsInstantiator,
    private val oxstsInliner: OxstsInliner,
    private val oxstsFlattener: OxstsFlattener,
    private val compilationArtifactManager: CompilationArtifactManager,
) {

    private val logger by loggerFactory()

    fun compile(classDeclaration: ClassDeclaration): InlinedOxsts {
        return compileDeflated(classDeclaration).inlinedOxsts
    }

    fun compileDeflated(classDeclaration: ClassDeclaration): FlattenedCompilationContext {
        logger.info { "Starting compilation of class '${classDeclaration.name}'" }
        val created = inlinedOxstsModelCreator.create(classDeclaration)
        return compilePipeline(created)
    }

    fun compile(inlinedOxsts: InlinedOxsts): InlinedOxsts {
        return compileDeflated(inlinedOxsts).inlinedOxsts
    }

    fun compileDeflated(inlinedOxsts: InlinedOxsts): FlattenedCompilationContext {
        logger.info { "Starting compilation of pre-inlined OXSTS '${inlinedOxsts.classDeclaration.name}'" }
        val created = CreatedCompilationContext(inlinedOxsts)
        return compilePipeline(created)
    }

    private fun compilePipeline(created: CreatedCompilationContext): FlattenedCompilationContext {
        compilationArtifactManager.setTarget(created.inlinedOxsts)

        val totalMark = markNow()

        logger.info { "Phase: instantiation" }
        val instantiationMark = markNow()
        val instantiatedContext = oxstsInstantiator.instantiate(created)
        compilationArtifactManager.commitInstantiated()
        logger.info { "Phase: instantiation done in ${instantiationMark.elapsedNow()}" }

        logger.info { "Phase: inlining" }
        val inliningMark = markNow()
        val inlinedContext = oxstsInliner.inline(instantiatedContext)
        compilationArtifactManager.commitInlined()
        logger.info { "Phase: inlining done in ${inliningMark.elapsedNow()}" }

        logger.info { "Phase: flattening" }
        val flatteningMark = markNow()
        val flattenedContext = oxstsFlattener.flatten(inlinedContext)
        compilationArtifactManager.commitFlattened()
        logger.info { "Phase: flattening done in ${flatteningMark.elapsedNow()}" }

        logger.info { "Compilation finished in ${totalMark.elapsedNow()}" }

        return flattenedContext
    }

}
