/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.inliner.OxstsInliner
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.OxstsInflator
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager
import hu.bme.mit.semantifyr.semantics.transformation.xsts.XstsTransformer
import org.eclipse.xtext.util.OnChangeEvictingCache

@CompilationScoped
class OxstsToXstsTransformer {

    @Inject
    private lateinit var inlinedOxstsModelManager: InlinedOxstsModelManager

    @Inject
    private lateinit var oxstsInflator: OxstsInflator

    @Inject
    private lateinit var oxstsInliner: OxstsInliner

    @Inject
    private lateinit var xstsTransformer: XstsTransformer

    @Inject
    private lateinit var compilationStateManager: CompilationStateManager

//    fun transform(model: SemantifyrModelContext, className: String, rewriteChoice: Boolean = false) {
//        val classDeclaration = model.streamClasses().firstOrNull {
//            it.name == className
//        }
//
//        if (classDeclaration == null) {
//            throw IllegalArgumentException("Could not find class named $className")
//        }
//
//        transform(classDeclaration, rewriteChoice)
//    }

    fun transform(progressContext: ProgressContext, classDeclaration: ClassDeclaration, rewriteChoice: Boolean = false) {
        val inlinedOxsts = inlinedOxstsModelManager.createInlinedOxsts(classDeclaration)

        compilationStateManager.initArtifactManager(inlinedOxsts, progressContext)

        progressContext.reportProgress("Instantiating model", 10)

        oxstsInflator.inflateInstanceModel(inlinedOxsts)

        progressContext.reportProgress("Inlining calls", 20)

        oxstsInliner.inlineOxsts(inlinedOxsts)

        progressContext.reportProgress("Deflating instances and structure", 60)

        oxstsInflator.deflateInstanceModel(inlinedOxsts)

        progressContext.reportProgress("Transforming to XSTS", 80)

        xstsTransformer.transform(inlinedOxsts, rewriteChoice)

        progressContext.reportProgress("Serializing final model", 90)

        compilationStateManager.finalizeArtifactManager(inlinedOxsts)
    }

}
