/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.transformation.WHOLE_DELAY_VARIABLE_NAME
import hu.bme.mit.semantifyr.backend.transformation.WholeDelay
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.xsts.lang.xsts.BooleanOp
import hu.bme.mit.semantifyr.xsts.lang.xsts.ComparisonOp
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

    fun transform(
        inlinedOxsts: InlinedOxsts,
        xstsUri: URI,
        wholeDelay: WholeDelay?,
    ): XstsModel {
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

        if (wholeDelay != null) {
            waitBeforeEveryStep(xsts, wholeDelay)
        }
        excludeDeclarationValuation(xsts)

        xsts.enumDeclarations += inlinedOxsts.eAllContents().asSequence().filterIsInstance<VariableDeclaration>().map {
            it.typeSpecification?.domain
        }.filterIsInstance<EnumDeclaration>().map {
            thetaDomainTransformer.transform(it)
        }.distinct()

        return xsts
    }

    private fun waitBeforeEveryStep(xsts: XstsModel, wholeDelay: WholeDelay) {
        val delay = XstsFactory.createTopLevelVariableDeclaration().also {
            it.name = WHOLE_DELAY_VARIABLE_NAME
            it.type = XstsFactory.createIntegerType()
            it.expression = integerLiteral(0)
        }
        xsts.variableDeclarations += delay

        for (branch in xsts.tran.branches) {
            val wait = listOf(
                XstsFactory.createHavocOperation().also {
                    it.reference = XstsFactory.createElementReferenceExpression().also { reference ->
                        reference.element = delay
                    }
                },
                XstsFactory.createAssumptionOperation().also {
                    it.expression = XstsFactory.createBooleanOperator().also { range ->
                        range.left = comparison(reference(delay), ComparisonOp.GREATER_EQ, integerLiteral(0))
                        range.op = BooleanOp.AND
                        range.right = comparison(reference(delay), ComparisonOp.LESS_EQ, integerLiteral(wholeDelay.longestDelay))
                    }
                },
            ) + wholeDelay.clocks.map {
                advance(thetaVariableTransformer.transformTopLevel(it.variable), delay, it.saturation)
            }
            branch.steps.addAll(0, wait)
        }
    }

    private fun advance(
        clock: XstsTopLevelVariableDeclaration,
        delay: XstsTopLevelVariableDeclaration,
        saturation: Int,
    ): XstsOperation {
        return XstsFactory.createAssignmentOperation().also {
            it.reference = reference(clock)
            it.expression = XstsFactory.createIfThenElseExpression().also { saturated ->
                saturated.guard = comparison(sum(clock, delay), ComparisonOp.GREATER_EQ, integerLiteral(saturation))
                saturated.then = integerLiteral(saturation)
                saturated.`else` = sum(clock, delay)
            }
        }
    }

    private fun sum(clock: XstsTopLevelVariableDeclaration, delay: XstsTopLevelVariableDeclaration): XstsExpression {
        return XstsFactory.createArithmeticBinaryOperator().also {
            it.left = reference(clock)
            it.op = XstsArithmeticOp.ADD
            it.right = reference(delay)
        }
    }

    private fun comparison(
        left: XstsExpression,
        op: ComparisonOp,
        right: XstsExpression,
    ): XstsExpression {
        return XstsFactory.createComparisonOperator().also {
            it.left = left
            it.op = op
            it.right = right
        }
    }

    private fun integerLiteral(value: Int): XstsExpression {
        return XstsFactory.createLiteralInteger().also {
            it.value = value
        }
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
