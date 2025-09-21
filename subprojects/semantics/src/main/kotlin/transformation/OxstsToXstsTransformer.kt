/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.XSTS
import hu.bme.mit.semantifyr.semantics.loading.SemantifyrModelContext
import hu.bme.mit.semantifyr.semantics.transformation.inliner.OxstsInliner
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.OxstsInflator
import hu.bme.mit.semantifyr.semantics.transformation.xsts.XstsTransformer

@Singleton
class OxstsToXstsTransformer {

    @Inject
    private lateinit var inlinedOxstsModelManager: InlinedOxstsModelManager

    @Inject
    private lateinit var oxstsInflator: OxstsInflator

    @Inject
    private lateinit var oxstsInliner: OxstsInliner

    @Inject
    private lateinit var xstsTransformer: XstsTransformer

    fun transform(model: SemantifyrModelContext, className: String, rewriteChoice: Boolean = false) {
        val classDeclaration = model.streamClasses().firstOrNull {
            it.name == className
        }

        if (classDeclaration == null) {
            throw IllegalArgumentException("Could not find class named $className")
        }

        transform(classDeclaration, rewriteChoice)
    }

    fun transform(classDeclaration: ClassDeclaration, rewriteChoice: Boolean = false) {
        inlinedOxstsModelManager.useInlinedModel(classDeclaration) { inlinedOxsts ->
//            oxstsInflator.inflateInstanceModel(inlinedOxsts)
//            oxstsInliner.inlineOxsts(inlinedOxsts)
//            oxstsInflator.deflateInstanceModel(inlinedOxsts)
//            xstsTransformer.transform(inlinedOxsts, rewriteChoice)
        }
    }

}
