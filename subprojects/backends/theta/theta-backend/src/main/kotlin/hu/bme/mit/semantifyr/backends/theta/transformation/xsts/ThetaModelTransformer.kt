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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.xsts.lang.xsts.BooleanOp
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

        excludeDeclarationValuation(xsts)

        xsts.enumDeclarations += inlinedOxsts.eAllContents().asSequence().filterIsInstance<VariableDeclaration>().map {
            it.typeSpecification?.domain
        }.filterIsInstance<EnumDeclaration>().map {
            thetaDomainTransformer.transform(it)
        }.distinct()

        return xsts
    }

    // Theta's CEGAR configurations test the property on the declaration valuation too, which is not a state
    private fun excludeDeclarationValuation(xsts: XstsModel) {
        val flag = XstsFactory.createTopLevelVariableDeclaration().also {
            it.isControl = true
            it.name = INITIALIZED_FLAG_NAME
            it.type = XstsFactory.createBooleanType()
            it.expression = booleanLiteral(false)
        }
        xsts.variableDeclarations += flag

        if (xsts.init.branches.isEmpty()) {
            xsts.init.branches += XstsFactory.createSequenceOperation()
        }
        for (branch in xsts.init.branches) {
            branch.steps += XstsFactory.createAssignmentOperation().also {
                it.reference = reference(flag)
                it.expression = booleanLiteral(true)
            }
        }

        val invariant = xsts.property.invariant
        xsts.property.invariant = XstsFactory.createBooleanOperator().also {
            it.left = XstsFactory.createNegationOperator().also { negation ->
                negation.body = reference(flag)
            }
            it.op = BooleanOp.OR
            it.right = invariant
        }
    }

    private fun reference(variable: XstsTopLevelVariableDeclaration): XstsExpression {
        return XstsFactory.createElementReferenceExpression().also {
            it.element = variable
        }
    }

    private fun booleanLiteral(value: Boolean): XstsExpression {
        return XstsFactory.createLiteralBoolean().also {
            it.isValue = value
        }
    }

    private fun transform(transitionDeclaration: TransitionDeclaration): XstsTransition {
        return XstsFactory.createTransition().also {
            for (branch in transitionDeclaration.branches) {
                it.branches += thetaOperationTransformer.transform(branch) as XstsSequenceOperation
            }
        }
    }

    companion object {
        const val INITIALIZED_FLAG_NAME = "__semantifyr_initialized"
    }
}
