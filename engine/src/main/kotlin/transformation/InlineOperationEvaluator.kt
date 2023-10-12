package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.*
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

fun <T : EObject> T.copy() = EcoreUtil2.copy(this)

class InlineOperationEvaluator(
    val context: InstanceObject
) {
    fun inlineTransition(operation: InlineOperation): Operation {
        return when (operation) {
            is InlineCall -> {
                val transition = context.expressionEvaluator.evaluateTypedReference<Transition>(operation.reference)

                OxstsFactory.createChoiceOperation().apply {
                    for (currentOperation in transition.operation) {
                        this.operation.add(EcoreUtil2.copy(currentOperation)) // TODO should not simply copy
                    }
                }
            }
            is InlineIfOperation -> {
                if (context.expressionEvaluator.evaluateBoolean(operation.guard)) {
                    operation.body.copy() // TODO should not simply copy
                } else {
                    operation.`else`?.copy() ?: OxstsFactory.createEmptyOperation() // TODO should not simply copy
                }
            }
//            is InlineSeq -> {
//                val body = ExpressionEvaluator.evaluateOperationSet(operation.reference)!!
//                OxstsFactory.createSequenceOperation()
//            }
//            is InlineChoice -> {
//                val body = ExpressionEvaluator.evaluateOperationSet(operation.reference)!!
//                OxstsFactory.createChoiceOperation().apply {
//                    this.operation.addAll()
//                }
//            }
            else -> error("Operation is not of known type: $operation")
        }
    }
}
