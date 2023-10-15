package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.CompositeOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Enum
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.IfOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.Operation
import hu.bme.mit.gamma.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable
import hu.bme.mit.gamma.oxsts.model.oxsts.VariableTypeReference
import hu.bme.mit.gamma.oxsts.model.oxsts.XSTS
import org.eclipse.xtext.EcoreUtil2
import java.util.LinkedList

class XstsTransformer {
    fun transform(target: Target): XSTS {
        val instantiator = Instantiator()
        val rootInstance = instantiator.instantiate(target)

        target.init.inlineOperations(rootInstance)
        target.init.rewriteChoiceElse()

        target.transition.inlineOperations(rootInstance)
        target.transition.rewriteChoiceElse()

        target.rewriteVariableExpressions(rootInstance)

        val xsts = OxstsFactory.createXSTS()

        val enums = target.variables.map {
            it.typing
        }.filterIsInstance<VariableTypeReference>().map {
            it.reference
        }.filterIsInstance<Enum>().toSet()

        xsts.enums += enums
        xsts.variables += target.variables
        xsts.init = target.init
        xsts.transition = target.transition
        xsts.property = target.property

        return xsts
    }

    fun Transition.inlineOperations(rootInstance: InstanceObject) {
        val processorQueue = LinkedList(operation)

        while (processorQueue.any()) {
            val operation = processorQueue.removeFirst()

            when (operation) {
                is InlineOperation -> {
                    val inlined = rootInstance.operationEvaluator.inlineTransition(operation)
                    EcoreUtil2.replace(operation, inlined)
                    processorQueue += inlined
                }
                is CompositeOperation -> {
                    processorQueue += operation.operation
                }
            }
        }
    }

    /**
     * TODO
     *
     * Else branches cannot be mapped simply like this.
     * This only works when the assume (...) operation is the first in a sequence, since otherwise
     * we must evaluate all preceding operations first, and THEN the guard expression -> hence
     * the simple inlining will not work.
     *
     * Other way: create "simulation" operations, meaning replace all real variable calls with
     * local variables, negate all assumptions and inline them in the else branch. This will
     * always behave correctly, although it is hard to calculate and would most likely reduce
     * performance on the theta side (without optimizations).
     */
    fun Transition.rewriteChoiceElse() {
        val choices = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).filter {
            it.`else` != null
        }

        for (choice in choices) {
            val assumption = choice.calculateAssumption()
            val assume = OxstsFactory.createAssumptionOperation(OxstsFactory.createNotOperator(assumption))
            val branch = OxstsFactory.createSequenceOperation().also {
                it.operation += assume
                it.operation += choice.`else`
            }

            choice.operation += branch
        }
    }

    fun Operation.calculateAssumption(): Expression {
        return when (this) {
            is AssumptionOperation -> expression.copy()
            is AssignmentOperation -> OxstsFactory.createLiteralBoolean(true)
            is HavocOperation -> OxstsFactory.createLiteralBoolean(true)
            is SequenceOperation -> {
                operation.map {
                    it.calculateAssumption()
                }.reduce { lhs, rhs ->
                    OxstsFactory.createAndOperator(lhs, rhs)
                }
            }
            is ChoiceOperation -> {
                operation.map {
                    it.calculateAssumption()
                }.reduce { lhs, rhs ->
                    OxstsFactory.createOrOperator(lhs, rhs)
                }
            }
            is IfOperation -> {
                val guardAssumption = guard.copy()
                val notGuardAssumption = OxstsFactory.createNotOperator(guard.copy())
                val bodyAssumption = body.calculateAssumption()
                val elseAssumption = `else`?.calculateAssumption() ?: OxstsFactory.createLiteralBoolean(true)

                OxstsFactory.createOrOperator(
                    OxstsFactory.createAndOperator(guardAssumption, bodyAssumption),
                    OxstsFactory.createAndOperator(notGuardAssumption, elseAssumption),
                )
            }
            else -> error("Unsupported operation!")
        }
    }

    fun Target.rewriteVariableExpressions(rootInstance: InstanceObject) {
        val expressions = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).filter {
            val declaration = it.chains.last() as DeclarationReferenceExpression
            declaration.element is Variable
        }

        for (expression in expressions) {
            val instanceObject = rootInstance.expressionEvaluator.evaluateInstanceObject(expression.exceptLast())
            val reference = expression.chains.last() as DeclarationReferenceExpression
            val variable = reference.element as Variable
            val newExpression = OxstsFactory.createChainReferenceExpression(instanceObject.variableMap[variable]!!)
            EcoreUtil2.replace(expression, newExpression)
        }
    }

}
