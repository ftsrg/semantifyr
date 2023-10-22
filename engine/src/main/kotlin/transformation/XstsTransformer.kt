package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.copy
import hu.bme.mit.gamma.oxsts.engine.utils.dropLast
import hu.bme.mit.gamma.oxsts.engine.utils.element
import hu.bme.mit.gamma.oxsts.engine.utils.isFeatureTyped
import hu.bme.mit.gamma.oxsts.engine.utils.lastChain
import hu.bme.mit.gamma.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.CompositeOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Enum
import hu.bme.mit.gamma.oxsts.model.oxsts.EqualityOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.InequalityOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.Package
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable
import hu.bme.mit.gamma.oxsts.model.oxsts.VariableTypeReference
import hu.bme.mit.gamma.oxsts.model.oxsts.XSTS
import org.eclipse.xtext.EcoreUtil2
import java.util.*

class XstsTransformer {

    fun transform(rootElements: List<Package>, targetName: String): XSTS {
        val target = rootElements.flatMap { it.target }.first {
            it.name == targetName
        }

        return transform(target)
    }

    private fun transform(target: Target): XSTS {
        val rootInstance = Instantiator.instantiateInstances(target)

        val xsts = OxstsFactory.createXSTS()

        xsts.variables += Instantiator.instantiateVariables(rootInstance)
        xsts.init = target.initTransition.single().copy() // TODO handle multiple inits?
        xsts.transition = target.mainTransition.single().copy() // TODO handle multiple trans?
        xsts.property = target.properties.single().copy() // TODO handle multiple props?

        xsts.init.inlineOperations(rootInstance)
        xsts.transition.inlineOperations(rootInstance)

        xsts.rewriteFeatureTypingVariableExpression(rootInstance)
        xsts.rewriteVariableAccesses(rootInstance)

        xsts.enums += xsts.variables.asSequence().map {
            it.typing
        }.filterIsInstance<VariableTypeReference>().map {
            it.reference
        }.filterIsInstance<Enum>().toSet()

        OperationOptimizer.optimize(xsts.init)
        OperationOptimizer.optimize(xsts.transition)

        return xsts
    }

    private fun Transition.inlineOperations(rootInstance: InstanceObject) {
        val processorQueue = LinkedList(operation)

        while (processorQueue.any()) {
            val operation = processorQueue.removeFirst()

            when (operation) {
                is InlineOperation -> {
                    val inlined = rootInstance.operationEvaluator.inlineOperation(operation)
                    EcoreUtil2.replace(operation, inlined)
                    processorQueue += inlined
                }

                is CompositeOperation -> {
                    processorQueue += operation.operation
                }
            }
        }
    }

//    /**
//     * TODO
//     *
//     * Else branches cannot be mapped simply like this.
//     * This only works when the assume (...) operation is the first in a sequence, since otherwise
//     * we must evaluate all preceding operations first, and THEN the guard expression -> hence
//     * the simple inlining will not work.
//     *
//     * Other way: create "simulation" operations, meaning replace all real variable calls with
//     * local variables, negate all assumptions and inline them in the else branch. This will
//     * always behave correctly, although it is hard to calculate and would most likely reduce
//     * performance on the theta side (without optimizations).
//     */
//    private fun Transition.rewriteChoiceElse() {
//        val choices = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).filter {
//            it.`else` != null
//        }
//
//        for (choice in choices) {
//            val assumption = choice.calculateAssumption()
//            val assume = OxstsFactory.createAssumptionOperation(OxstsFactory.createNotOperator(assumption))
//            val branch = OxstsFactory.createSequenceOperation().also {
//                it.operation += assume
//                it.operation += choice.`else`
//            }
//
//            choice.operation += branch
//        }
//    }
//
//    private fun Operation.calculateAssumption(): Expression {
//        return when (this) {
//            is AssumptionOperation -> expression.copy()
//            is AssignmentOperation -> OxstsFactory.createLiteralBoolean(true)
//            is HavocOperation -> OxstsFactory.createLiteralBoolean(true)
//            is SequenceOperation -> {
//                operation.map {
//                    it.calculateAssumption()
//                }.reduce { lhs, rhs ->
//                    OxstsFactory.createAndOperator(lhs, rhs)
//                }
//            }
//
//            is ChoiceOperation -> {
//                operation.map {
//                    it.calculateAssumption()
//                }.reduce { lhs, rhs ->
//                    OxstsFactory.createOrOperator(lhs, rhs)
//                }
//            }
//
//            is IfOperation -> {
//                val guardAssumption = guard.copy()
//                val notGuardAssumption = OxstsFactory.createNotOperator(guard.copy())
//                val bodyAssumption = body.calculateAssumption()
//                val elseAssumption = `else`?.calculateAssumption() ?: OxstsFactory.createLiteralBoolean(true)
//
//                OxstsFactory.createOrOperator(
//                    OxstsFactory.createAndOperator(guardAssumption, bodyAssumption),
//                    OxstsFactory.createAndOperator(notGuardAssumption, elseAssumption),
//                )
//            }
//
//            else -> error("Unsupported operation!")
//        }
//    }

    private fun XSTS.rewriteVariableAccesses(rootInstance: InstanceObject) {
        val referenceExpressions = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).filter {
            val declaration = it.chains.last() as? DeclarationReferenceExpression
            declaration?.element is Variable
        }

        for (referenceExpression in referenceExpressions) {
            val reference = referenceExpression.chains.last() as DeclarationReferenceExpression
            val oldVariable = reference.element as Variable

            val instanceObject = rootInstance.expressionEvaluator.evaluateInstanceObject(referenceExpression.dropLast(1))
            val transformedVariable = instanceObject.variableMap[oldVariable]!!

            val newExpression = OxstsFactory.createChainReferenceExpression(transformedVariable)
            EcoreUtil2.replace(referenceExpression, newExpression)
        }
    }

    private fun XSTS.rewriteFeatureTypingVariableExpression(rootInstance: InstanceObject) {
        val expressions = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).filter {
            (it.lastChain().element as? Variable)?.isFeatureTyped == true
        }

        for (expression in expressions) {
            val oldVariable = expression.lastChain().element!! as Variable

            rewriteFeatureTypingVariableExpression(oldVariable, expression, rootInstance)
        }
    }

    private fun rewriteFeatureTypingVariableExpression(variable: Variable, referenceExpression: ChainReferenceExpression, rootInstance: InstanceObject) {
        val parent = referenceExpression.eContainer()

        when (parent) {
            is AssignmentOperation -> {
                if (parent.reference != referenceExpression) return

                val newExpression = rootInstance.variableTransformer.transformExpression(parent.reference, parent.expression as ReferenceExpression, variable)

                EcoreUtil2.replace(parent.expression, newExpression)
            }
            is EqualityOperator -> {
                val operandIndex = parent.operands.indexOf(referenceExpression)

                val otherOperandIndex = if (operandIndex == 0) 1 else 0
                val otherOperand = parent.operands[otherOperandIndex]!! as ReferenceExpression

                val newExpression = rootInstance.variableTransformer.transformExpression(referenceExpression, otherOperand, variable)

                EcoreUtil2.replace(otherOperand, newExpression)
            }
            is InequalityOperator -> {
                val operandIndex = parent.operands.indexOf(referenceExpression)

                val otherOperandIndex = if (operandIndex == 0) 1 else 0
                val otherOperand = parent.operands[otherOperandIndex]!! as ReferenceExpression

                val newExpression = rootInstance.variableTransformer.transformExpression(referenceExpression, otherOperand, variable)

                EcoreUtil2.replace(otherOperand, newExpression)
            }
            else -> error("Unknown reference: $parent")
        }
    }

}
