/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.XSTS
import hu.bme.mit.semantifyr.semantics.optimization.InlinedOxstsOperationOptimizer
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy

@CompilationScoped
class XstsTransformer {

    @Inject
    private lateinit var choiceElseRewriter: ChoiceElseRewriter

    @Inject
    private lateinit var inlinedOxstsOperationOptimizer: InlinedOxstsOperationOptimizer

    fun transform(inlinedOxsts: InlinedOxsts, rewriteChoice: Boolean): XSTS {
        if (rewriteChoice) {
            choiceElseRewriter.rewriteChoiceElse(inlinedOxsts.initTransition)
            choiceElseRewriter.rewriteChoiceElse(inlinedOxsts.mainTransition)
        }

        inlinedOxstsOperationOptimizer.optimize(inlinedOxsts)

        return createXsts(inlinedOxsts)
    }

    private fun createXsts(inlinedOxsts: InlinedOxsts): XSTS {
        val copy = inlinedOxsts.copy()
        val xsts = OxstsFactory.createXSTS()

        xsts.variables += copy.variables
        xsts.mainTransition = copy.mainTransition
        xsts.initTransition = copy.initTransition
        xsts.property = copy.property
        xsts.enums += xsts.variables.asSequence().map {
            it.type
        }.filterIsInstance<EnumDeclaration>().distinct()

        return xsts
    }

}
