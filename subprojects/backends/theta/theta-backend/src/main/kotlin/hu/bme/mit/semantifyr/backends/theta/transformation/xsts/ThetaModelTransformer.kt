/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.xsts.lang.xsts.SequenceOperation
import hu.bme.mit.semantifyr.xsts.lang.xsts.Transition
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import org.eclipse.emf.common.util.URI

private typealias XstsTransition = Transition
private typealias XstsSequenceOperation = SequenceOperation

class ThetaModelTransformer @Inject constructor(
    private val thetaOperationTransformer: ThetaOperationTransformer,
    private val thetaPropertyTransformer: ThetaPropertyTransformer,
    private val thetaDomainTransformer: ThetaDomainTransformer,
    private val thetaVariableTransformer: ThetaVariableTransformer,
    private val xstsModelCreator: XstsModelCreator,
) {

    fun transform(inlinedOxsts: InlinedOxsts, xstsUri: URI): XstsModel {
        val xsts = xstsModelCreator.createEmptyXsts(xstsUri)

        for (variableDeclaration in inlinedOxsts.variables) {
            xsts.variableDeclarations += thetaVariableTransformer.transformTopLevel(variableDeclaration)
        }

        xsts.env = XstsFactory.createTransition().also {
            it.branches += XstsFactory.createSequenceOperation()
        }
        xsts.tran = transform(inlinedOxsts.mainTransition)
        xsts.init = transform(inlinedOxsts.initTransition)
        xsts.property = thetaPropertyTransformer.transform(inlinedOxsts.property)

        xsts.enumDeclarations += inlinedOxsts.variables.asSequence().map {
            it.typeSpecification.domain
        }.filterIsInstance<EnumDeclaration>().map {
            thetaDomainTransformer.transform(it)
        }.distinct()

        return xsts
    }

    private fun transform(transitionDeclaration: TransitionDeclaration): XstsTransition {
        return XstsFactory.createTransition().also {
            for (branch in transitionDeclaration.branches) {
                it.branches += thetaOperationTransformer.transform(branch) as XstsSequenceOperation
            }
        }
    }
}
