/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.CompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.OxstsFlattener
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.OxstsInliner
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.OxstsInstantiator

class CompilationPipeline @Inject constructor(
    private val inlinedOxstsModelCreator: InlinedOxstsModelCreator,
    private val oxstsInstantiator: OxstsInstantiator,
    private val oxstsInliner: OxstsInliner,
    private val oxstsFlattener: OxstsFlattener,
    private val compilationArtifactManager: CompilationArtifactManager,
) {

    fun compile(classDeclaration: ClassDeclaration): InlinedOxsts {
        return compileDeflated(classDeclaration).inlinedOxsts
    }

    fun compileDeflated(classDeclaration: ClassDeclaration): FlattenedCompilationContext {
        val compilationContext = inlinedOxstsModelCreator.create(classDeclaration)
        return compilePipeline(compilationContext)
    }

    fun compile(inlinedOxsts: InlinedOxsts): InlinedOxsts {
        return compileDeflated(inlinedOxsts).inlinedOxsts
    }

    fun compileDeflated(inlinedOxsts: InlinedOxsts): FlattenedCompilationContext {
        val compilationContext = CompilationContext(inlinedOxsts)
        return compilePipeline(compilationContext)
    }

    private fun compilePipeline(compilationContext: CompilationContext): FlattenedCompilationContext {
        compilationArtifactManager.setTarget(compilationContext.inlinedOxsts)

        val instantiatedContext = oxstsInstantiator.instantiate(compilationContext)

        compilationArtifactManager.commitInstantiated()

        val inlinedContext = oxstsInliner.inline(instantiatedContext)

        compilationArtifactManager.commitInlined()

        val flattenedContext = oxstsFlattener.flatten(inlinedContext)

        compilationArtifactManager.commitFlattened()

        return flattenedContext
    }

}
