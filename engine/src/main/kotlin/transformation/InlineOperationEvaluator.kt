package hu.bme.mit.gamma.oxsts.engine.transformation

import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

fun <T : EObject> T.copy() = EcoreUtil2.copy(this)

class InlineOperationEvaluator {
//    fun inlineTransition(operation: InlineOperation): Operation {
//        return when (operation) {
//            is InlineCall -> {
//                val body = ExpressionEvaluator.evaluateOperation(operation.reference)!!
//                EcoreUtil2.copy(body)
//            }
//            is InlineIfOperation -> {
//                if (ExpressionEvaluator.evaluateBoolean(operation.guard)) {
//                    operation.body.copy()
//                } else {
//                    operation.`else`?.copy() ?: OxstsFactory.createSequenceOperation()
//                }
//            }
//            is InlineSeq -> {
//                val body = ExpressionEvaluator.evaluateOperationSet(operation.reference)!!
//                OxstsFactory.createSequenceOperation()
//            }
//            is InlineChoice -> {
//                val body = ExpressionEvaluator.evaluateOperationSet(operation.reference)!!
//                OxstsFactory.createSequenceOperation()
//            }
//            else -> error("Operation is not of known type: $operation")
//        }
//    }
}
