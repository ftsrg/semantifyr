package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.AndOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.NotOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.OrOperator
import org.eclipse.xtext.EcoreUtil2

object ExpressionOptimizer {

    fun optimize(expression: Expression): Boolean {
        var anyOptimized = false
        var optimized: Boolean

        do {
            optimized = expression.optimizeInternal()
            anyOptimized = anyOptimized || optimized
        } while (optimized)

        return anyOptimized
    }

    private fun Expression.optimizeInternal(): Boolean {
        return optimizeNotOfLiteral() ||
//                optimizeRedundantTrue() ||
//                optimizeRedundantFalse() ||
//                propagateAllFalseAnd() ||
//                propagateAllTrueAnd() ||
//                propagateAllFalseOr() ||
//                propagateAllTrueOr() ||
                replaceRedundantOr() ||
                replaceRedundantAnd()
    }

    private fun Expression.optimizeRedundantTrue(): Boolean {
        val literals = EcoreUtil2.getAllContentsOfType(this, LiteralBoolean::class.java).filter {
            it.isValue && it.eContainer() is OrOperator
        }

        if (literals.isEmpty()) {
            return false
        }

        for (literal in literals) {
            EcoreUtil2.remove(literal)
        }

        return true
    }

    private fun Expression.optimizeRedundantFalse(): Boolean {
        val literals = EcoreUtil2.getAllContentsOfType(this, LiteralBoolean::class.java).filter {
            !it.isValue && it.eContainer() is AndOperator
        }

        if (literals.isEmpty()) {
            return false
        }

        for (literal in literals) {
            EcoreUtil2.remove(literal)
        }

        return true
    }

    private fun Expression.optimizeNotOfLiteral(): Boolean {
        val nots = EcoreUtil2.getAllContentsOfType(this, NotOperator::class.java).filter {
            it.operands.any {
                it is LiteralBoolean
            }
        }

        if (nots.isEmpty()) {
            return false
        }

        for (not in nots) {
            val literal = not.operands.single() as LiteralBoolean
            val oppositeLiteral = OxstsFactory.createLiteralBoolean(!literal.isValue)
            EcoreUtil2.replace(not, oppositeLiteral)
        }

        return true
    }

    private fun Expression.replaceRedundantOr(): Boolean {
        val ors = EcoreUtil2.getAllContentsOfType(this, OrOperator::class.java).filter {
            it.operands.any {
                it is LiteralBoolean && !it.isValue
            }
        }

        if (ors.isEmpty()) {
            return false
        }

        for (or in ors) {
            val otherOperand = or.operands.firstOrNull { it !is LiteralBoolean } ?: or.operands.first()

            EcoreUtil2.replace(or, otherOperand)
        }

        return true
    }

    private fun Expression.replaceRedundantAnd(): Boolean {
        val ands = EcoreUtil2.getAllContentsOfType(this, AndOperator::class.java).filter {
            it.operands.any {
                it is LiteralBoolean && it.isValue
            }
        }

        if (ands.isEmpty()) {
            return false
        }

        for (and in ands) {
            val otherOperand = and.operands.firstOrNull { it !is LiteralBoolean } ?: and.operands.first()

            EcoreUtil2.replace(and, otherOperand)
        }

        return true
    }

    private fun Expression.propagateAllTrueOr(): Boolean {
        val ors = EcoreUtil2.getAllContentsOfType(this, OrOperator::class.java).filter {
            it.operands.any {
                if (it is LiteralBoolean) {
                    it.isValue
                }
                false
            }
        }

        if (ors.isEmpty()) {
            return false
        }

        for (or in ors) {
            EcoreUtil2.replace(or, OxstsFactory.createLiteralBoolean(true))
        }

        return true
    }

    private fun Expression.propagateAllFalseOr(): Boolean {
        val ors = EcoreUtil2.getAllContentsOfType(this, OrOperator::class.java).filter {
            it.operands.all {
                if (it is LiteralBoolean) {
                    !it.isValue
                }
                false
            }
        }

        if (ors.isEmpty()) {
            return false
        }

        for (or in ors) {
            EcoreUtil2.replace(or, OxstsFactory.createLiteralBoolean(false))
        }

        return true
    }

    private fun Expression.propagateAllFalseAnd(): Boolean {
        val ands = EcoreUtil2.getAllContentsOfType(this, AndOperator::class.java).filter {
            it.operands.any {
                if (it is LiteralBoolean) {
                    !it.isValue
                }
                false
            }
        }

        if (ands.isEmpty()) {
            return false
        }

        for (and in ands) {
            EcoreUtil2.replace(and, OxstsFactory.createLiteralBoolean(false))
        }

        return true
    }

    private fun Expression.propagateAllTrueAnd(): Boolean {
        val ands = EcoreUtil2.getAllContentsOfType(this, AndOperator::class.java).filter {
            it.operands.all {
                if (it is LiteralBoolean) {
                    it.isValue
                }
                false
            }
        }

        if (ands.isEmpty()) {
            return false
        }

        for (and in ands) {
            EcoreUtil2.replace(and, OxstsFactory.createLiteralBoolean(true))
        }

        return true
    }

}
