/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CompositeOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Enum
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EqualityOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Feature
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InequalityOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceTyping
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Target
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Variable
import hu.bme.mit.semantifyr.oxsts.model.oxsts.XSTS
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.evaluation.BooleanData
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.evaluation.IntegerData
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.instantiation.Instantiator
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.optimization.ExpressionOptimizer.optimize
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.optimization.OperationOptimizer.optimize
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite.ChoiceElseRewriter.rewriteChoiceElse
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite.ExpressionRewriter.rewriteReferences
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.contextualEvaluator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.copy
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.dropLast
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.element
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.findInitTransition
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.findMainTransition
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.findProperty
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isFeatureTyped
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.lastChain
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.operationInliner
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.referencedElement
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.type
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.variableTransformer
import org.eclipse.xtext.EcoreUtil2
import java.util.*

class XstsTransformer(
    val reader: OxstsReader
) {
    val logger by loggerFactory()

    fun transform(typeName: String, rewriteChoice: Boolean = false): XSTS {
        val type = reader.rootElements.flatMap { it.types }.filterIsInstance<Target>().first {
            it.name == typeName
        }

        return transform(type, rewriteChoice)
    }

    fun transform(target: Target, rewriteChoice: Boolean = false): XSTS {
        val xsts = OxstsFactory.createXSTS()

        logger.info("Instantiating target ${target.name}")

        val rootInstance = Instantiator.instantiateTree(target)

        xsts.variables += Instantiator.instantiateVariablesTree(rootInstance)

        logger.info("Transforming transitions")

        val init = rootInstance.contextualEvaluator.evaluateTransition(OxstsFactory.createChainReferenceExpression(OxstsFactory.createDeclarationReferenceExpression(rootInstance.type.findInitTransition())))
        val tran = rootInstance.contextualEvaluator.evaluateTransition(OxstsFactory.createChainReferenceExpression(OxstsFactory.createDeclarationReferenceExpression(rootInstance.type.findMainTransition())))
        val property = target.findProperty()

        xsts.init = init.copy()
        xsts.transition = tran.copy()
        xsts.property = property.copy()

        xsts.init.inlineOperations(rootInstance)
        xsts.transition.inlineOperations(rootInstance)

        logger.info("Rewriting operations")

        xsts.rewriteFeatureTypingVariableExpression(rootInstance)
        xsts.rewriteVariableAccesses(rootInstance)
        xsts.rewriteFeatureRefences(rootInstance)

        xsts.enums += xsts.variables.asSequence().map {
            it.typing
        }.filterIsInstance<ReferenceTyping>().map {
            it.referencedElement
        }.filterIsInstance<Enum>().toSet()

        logger.info("Optimizing XSTS model")

        xsts.init.optimize()
        xsts.transition.optimize()
        xsts.property.invariant.optimize()

        if (rewriteChoice) {
            logger.info("Rewriting choice-else operations")

            xsts.init.rewriteChoiceElse()
            xsts.transition.rewriteChoiceElse()
        }

        logger.info("Transformation done!")

        return xsts
    }

    private fun Transition.inlineOperations(rootInstance: Instance) {
        val processorQueue = LinkedList(operation)

        while (processorQueue.any()) {
            val operation = processorQueue.removeFirst()

            when (operation) {
                is InlineOperation -> {
                    val inlined = rootInstance.operationInliner.inlineOperation(operation)
                    EcoreUtil2.replace(operation, inlined)
                    processorQueue += inlined
                }
                is IfOperation -> {
                    processorQueue += operation.body
                    if (operation.`else` != null) {
                        processorQueue += operation.`else`
                    }
                }
                is ChoiceOperation -> {
                    processorQueue += operation.operation
                    if (operation.`else` != null) {
                        processorQueue += operation.`else`
                    }
                }
                is CompositeOperation -> {
                    processorQueue += operation.operation
                }
            }
        }
    }

    private fun Transition.rewriteChoiceElse() {
        for (op in operation) {
            op.rewriteChoiceElse()
        }
    }

    private fun Operation.rewriteChoiceElse() {
        when (this) {
            is ChoiceOperation -> {
                for (op in operation) {
                    op.rewriteChoiceElse()
                }

                if (`else` != null) {
                    `else`.rewriteChoiceElse()
                }
            }
            is IfOperation -> {
                body.rewriteChoiceElse()

                if (`else` != null) {
                    `else`.rewriteChoiceElse()
                }
            }
            is CompositeOperation -> {
                for (op in operation) {
                    op.rewriteChoiceElse()
                }
            }
        }

        if (this !is ChoiceOperation) return
        if (`else` == null) return

        val assumption = calculateAssumption()
        val notAssumption = OxstsFactory.createNotOperator(assumption)
        ExpressionOptimizer.optimize(notAssumption)
        val assume = OxstsFactory.createAssumptionOperation(notAssumption)
        val branch = OxstsFactory.createSequenceOperation().also {
            it.operation += assume
            if (`else` != null) {
                it.operation += `else`
            }
        }

        operation += branch
    }

    private fun Operation.calculateAssumption(): Expression {
        return when (this) {
            is AssumptionOperation -> expression.copy()
            is AssignmentOperation -> OxstsFactory.createLiteralBoolean(true)
            is HavocOperation -> OxstsFactory.createLiteralBoolean(true)
            is SequenceOperation -> {
                // all branches can be executed
                operation.map {
                    it.calculateAssumption()
                }.reduceOrNull { lhs, rhs ->
                    OxstsFactory.createAndOperator(lhs, rhs)
                } ?: OxstsFactory.createLiteralBoolean(true)
            }
            is ChoiceOperation -> {
                // any branch can be executed
                operation.map {
                    it.calculateAssumption()
                }.reduceOrNull { lhs, rhs ->
                    OxstsFactory.createOrOperator(lhs, rhs)
                } ?: OxstsFactory.createLiteralBoolean(true)
            }
            is IfOperation -> {
                val guardAssumption = guard.copy()
                val notGuardAssumption = OxstsFactory.createNotOperator(guard.copy())
                val bodyAssumption = body.calculateAssumption()
                val elseAssumption = `else`?.calculateAssumption() ?: OxstsFactory.createLiteralBoolean(true)

                // if can be executed, if the guard is true and the body can be executed,
                //  or the guard is false and the else can be executed
                OxstsFactory.createOrOperator(
                    OxstsFactory.createAndOperator(guardAssumption, bodyAssumption),
                    OxstsFactory.createAndOperator(notGuardAssumption, elseAssumption),
                )
            }
            else -> error("Unknown operation: $this!")
        }
    }

    private fun XSTS.rewriteVariableAccesses(rootInstance: Instance) {
        val referenceExpressions = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).filter {
            val declaration = it.chains.last() as? DeclarationReferenceExpression
            declaration?.element is Variable
        }

        for (referenceExpression in referenceExpressions) {
            val reference = referenceExpression.chains.last() as DeclarationReferenceExpression
            val oldVariable = reference.element as Variable

            val instance = rootInstance.contextualEvaluator.evaluateInstance(referenceExpression.dropLast(1))
            val transformedVariable = instance.variableTransformer.findTransformedVariable(oldVariable)

            val newExpression = OxstsFactory.createChainReferenceExpression(transformedVariable)
            EcoreUtil2.replace(referenceExpression, newExpression)
        }
    }

    private fun XSTS.rewriteFeatureRefences(rootInstance: Instance) {
        val references = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).filter {
            it.lastChain().element is Feature
        }

        for (reference in references) {
            val evaluation = rootInstance.contextualEvaluator.evaluate(reference)

            val expression = when (evaluation) {
                is BooleanData -> OxstsFactory.createLiteralBoolean(evaluation.value)
                is IntegerData -> OxstsFactory.createLiteralInteger(evaluation.value)
                else -> error("Feature reference is not an XSTS-compatible expression type!")
            }

            EcoreUtil2.replace(reference, expression)
        }
    }

    private fun XSTS.rewriteFeatureTypingVariableExpression(rootInstance: Instance) {
        val expressions = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).filter {
            (it.lastChain().element as? Variable)?.isFeatureTyped == true
        }

        for (expression in expressions) {
            val oldVariable = expression.lastChain().element!! as Variable

            rewriteFeatureTypingVariableExpression(oldVariable, expression, rootInstance)
        }
    }

    private fun rewriteFeatureTypingVariableExpression(variable: Variable, referenceExpression: ChainReferenceExpression, rootInstance: Instance) {
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
