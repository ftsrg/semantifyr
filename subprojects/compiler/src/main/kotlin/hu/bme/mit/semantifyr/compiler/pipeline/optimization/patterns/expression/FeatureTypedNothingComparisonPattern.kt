/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class FeatureTypedNothingComparisonPattern(
    private val metaEvaluatorProvider: MetaConstantExpressionEvaluatorProvider,
    private val multiplicityRangeEvaluator: MultiplicityRangeEvaluator,
) : OptimizationPattern {

    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is ComparisonOperator) {
            return false
        }
        val op = element.op
        if (op != ComparisonOp.EQ && op != ComparisonOp.NOT_EQ) {
            return false
        }

        val variableSide = variableSideFacingNothing(element.left, element.right) ?: return false
        val resolved = try {
            metaEvaluatorProvider.evaluate(variableSide)
        } catch (_: Exception) {
            return false
        }
        if (resolved !is VariableDeclaration) {
            return false
        }

        val typeSpecification = resolved.typeSpecification ?: return false
        if (typeSpecification.domain !is FeatureDeclaration) {
            return false
        }

        val range = try {
            multiplicityRangeEvaluator.evaluate(typeSpecification)
        } catch (_: Exception) {
            return false
        }
        if (range.lowerBound < 1) {
            return false
        }

        val parent = element.eContainer() ?: return false
        val folded = OxstsFactory.createLiteralBoolean(op == ComparisonOp.NOT_EQ)
        EcoreUtil2.replace(element, folded)
        worklist.add(parent)
        return true
    }

    private fun variableSideFacingNothing(left: Expression, right: Expression): Expression? {
        return when {
            right is LiteralNothing -> left
            left is LiteralNothing -> right
            else -> null
        }
    }

}
