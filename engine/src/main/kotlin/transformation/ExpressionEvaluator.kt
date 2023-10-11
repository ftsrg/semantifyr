package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.AndOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.HavocTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.InitTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.gamma.oxsts.model.oxsts.MainTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import org.eclipse.emf.ecore.EObject

//object LiteralNothing

//class ExpressionEvaluator(
//    val context: InstanceObject
//) {
//
//    fun EObject.evaluateIntegerExpression(expression: Expression): Int {
//        return 0
//    }
//
//    fun EObject.evaluateExpression(expression: Expression): Boolean {
//        val result = evaluate<LiteralBoolean>(expression)
//
//        return result.isValue
//    }
//
//    fun EObject.evaluateInteger(expression: Expression): Int {
//        val result = evaluate<LiteralInteger>(expression)
//
//        return result.value
//    }
//
//    inline fun <reified T> EObject.evaluate(expression: Expression): T {
//        val result = evaluate(expression)
//
//        if (result !is T) {
//            error("Expression is not of type ${T::class.qualifiedName}")
//        }
//
//        return result
//    }
//
//    fun EObject.evaluate(expression: Expression) = when (expression) {
//        is OperatorExpression -> evaluateOperator(expression)
//        is LiteralExpression -> evaluateLiteral(expression)
//        is ReferenceExpression -> evaluateReference(expression)
//        else -> error("Expression is not of known type: $expression")
//    }
//
//    fun EObject.evaluateOperator(operator: OperatorExpression) = when(operator) {
//        is AndOperator -> operator
//        else -> error("Operator is not of known type: $operator")
//    }
//
//    fun EObject.evaluateLiteral(literal: LiteralExpression) = when (literal) {
//        is LiteralInteger -> literal.value
//        is LiteralBoolean -> literal.isValue
//        is LiteralNothing -> LiteralNothing
//        else-> error("Literal is not of known type: $literal")
//    }
//    fun EObject.evaluateReference(reference: ReferenceExpression) = when (reference) {
//        is ChainReferenceExpression -> reference
//        is DeclarationReferenceExpression -> reference.element
//        is HavocTransitionExpression -> this//.havoc
//        is InitTransitionExpression -> this//.init
//        is MainTransitionExpression -> this//.main
//        else-> error("Reference is not of known type: $reference")
//    }
//
//}
