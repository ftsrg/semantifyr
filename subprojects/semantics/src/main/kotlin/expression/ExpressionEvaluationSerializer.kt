/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ArrayEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.BooleanEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EnumLiteralEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.InfinityEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.NothingEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RealEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.StringEvaluation

class ExpressionEvaluationSerializer : StaticExpressionEvaluationVisitor<String>() {

    fun serialize(evaluation: ExpressionEvaluation): String {
        return visit(evaluation)
    }

    @Inject
    private lateinit var oxstsQualifiedNameProvider: OxstsQualifiedNameProvider

    override fun visit(evaluation: ArrayEvaluation): String {
        val values = evaluation.elements.joinToString(", ") {
            visit(it)
        }

        return "[$values]"
    }

    override fun visit(evaluation: BooleanEvaluation): String {
        return evaluation.value.toString()
    }

    override fun visit(evaluation: EnumLiteralEvaluation): String {
        return oxstsQualifiedNameProvider.getFullyQualifiedNameString(evaluation.literal)
    }

    override fun visit(evaluation: InfinityEvaluation): String {
        return "infinity"
    }

    override fun visit(evaluation: IntegerEvaluation): String {
        return evaluation.value.toString()
    }

    override fun visit(evaluation: NothingEvaluation): String {
        return "nothing"
    }

    override fun visit(evaluation: RangeEvaluation): String {
        return "${evaluation.lowerBound}..${evaluation.upperBound}"
    }

    override fun visit(evaluation: RealEvaluation): String {
        return evaluation.value.toString()
    }

    override fun visit(evaluation: StringEvaluation): String {
        return evaluation.value
    }

    override fun visit(evaluation: InstanceEvaluation): String {
        if (evaluation.instances.size > 1) {
            val values = evaluation.instances.joinToString(", ") {
                oxstsQualifiedNameProvider.getFullyQualifiedNameString(it.domain)
            }

            return "[$values]"
        }

        return oxstsQualifiedNameProvider.getFullyQualifiedNameString(evaluation.instances.single().domain)
    }

}
