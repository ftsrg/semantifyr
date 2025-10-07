/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.xsts

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.XSTS
import hu.bme.mit.semantifyr.semantics.optimization.InlinedOxstsOperationOptimizer
import hu.bme.mit.semantifyr.semantics.optimization.XstsExpressionOptimizer
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy

@Singleton
class XstsTransformer {

    @Inject
    private lateinit var choiceElseRewriter: ChoiceElseRewriter

    @Inject
    private lateinit var inlinedOxstsOperationOptimizer: InlinedOxstsOperationOptimizer

    @Inject
    private lateinit var xstsExpressionOptimizer: XstsExpressionOptimizer

    fun transform(inlinedOxsts: InlinedOxsts, rewriteChoice: Boolean): XSTS {
        if (rewriteChoice) {
            choiceElseRewriter.rewriteChoiceElse(inlinedOxsts.initTransition)
            choiceElseRewriter.rewriteChoiceElse(inlinedOxsts.mainTransition)
        }

        inlinedOxstsOperationOptimizer.optimize(inlinedOxsts.initTransition)
        inlinedOxstsOperationOptimizer.optimize(inlinedOxsts.mainTransition)
        xstsExpressionOptimizer.optimize(inlinedOxsts.property)

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
