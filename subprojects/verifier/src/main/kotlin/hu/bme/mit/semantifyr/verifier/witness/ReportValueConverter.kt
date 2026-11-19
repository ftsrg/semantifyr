/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluationVisitor
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ExpressionEvaluationSerializer
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
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
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.verifier.ReportType
import hu.bme.mit.semantifyr.verifier.ReportTypeKind
import hu.bme.mit.semantifyr.verifier.ReportValue

class ReportValueConverter @Inject constructor(
    private val oxstsQualifiedNameProvider: OxstsQualifiedNameProvider,
    private val instanceReferenceProvider: InstanceReferenceProvider,
    private val expressionSerializer: ExpressionSerializer,
    private val expressionEvaluationSerializer: ExpressionEvaluationSerializer,
) {

    fun path(expression: Expression): String {
        return expressionSerializer.serialize(expression)
    }

    fun path(evaluation: InstanceEvaluation): String {
        return expressionEvaluationSerializer.serialize(evaluation)
    }

    fun value(expression: Expression): ReportValue {
        return when (expression) {
            is LiteralInteger -> ReportValue.Integer(expression.value.toBigInteger())
            is ArithmeticUnaryOperator -> negativeInteger(expression)
            is LiteralBoolean -> ReportValue.Bool(expression.isValue)
            is LiteralReal -> ReportValue.Real(expression.value)
            is LiteralString -> ReportValue.Text(expression.value)
            is LiteralNothing -> ReportValue.Null
            is ArrayLiteral -> ReportValue.Array(
                expression.values.map {
                    value(it)
                },
            )
            is RecordLiteral -> ReportValue.Record(
                expression.fields.associate {
                    it.field.name to value(it.expression)
                },
            )
            else -> reference(expression)
        }
    }

    private fun negativeInteger(expression: ArithmeticUnaryOperator): ReportValue {
        val body = expression.body
        if (expression.op != UnaryOp.MINUS || body !is LiteralInteger) {
            error("a witness value is a literal, found ${path(expression)}")
        }
        return ReportValue.Integer(body.value.toBigInteger().negate())
    }

    private fun reference(expression: Expression): ReportValue {
        val literal = (expression as? ElementReference)?.element as? EnumLiteral
        if (literal != null) {
            return ReportValue.Text(literal.name)
        }
        return ReportValue.Text(path(expression))
    }

    fun value(amount: Fraction): ReportValue {
        if (amount.isWhole) {
            return ReportValue.Integer(amount.numerator)
        }
        return ReportValue.Text(amount.toString())
    }

    fun value(evaluation: ExpressionEvaluation): ReportValue {
        return evaluationConverter.convert(evaluation)
    }

    fun type(typeSpecification: TypeSpecification?): ReportType {
        val domain = typeSpecification?.domain ?: error("a traced value has no declared type")
        val kind = when (domain) {
            is DataTypeDeclaration -> ReportTypeKind.Datatype
            is EnumDeclaration -> ReportTypeKind.Enum
            is ClassDeclaration -> ReportTypeKind.Class
            is RecordDeclaration -> ReportTypeKind.Record
            is FeatureDeclaration -> ReportTypeKind.Feature
            else -> error("a traced value has the type ${domain::class.simpleName}, which a report cannot name")
        }
        return ReportType(
            kind = kind,
            name = oxstsQualifiedNameProvider.getFullyQualifiedNameString(domain),
        )
    }

    private val evaluationConverter = EvaluationConverter()

    private inner class EvaluationConverter : CompileTimeExpressionEvaluationVisitor<ReportValue>() {
        fun convert(evaluation: ExpressionEvaluation): ReportValue {
            return visit(evaluation)
        }

        override fun visit(evaluation: ArrayEvaluation): ReportValue {
            return ReportValue.Array(
                evaluation.elements.map {
                    visit(it)
                },
            )
        }

        override fun visit(evaluation: BooleanEvaluation): ReportValue {
            return ReportValue.Bool(evaluation.value)
        }

        override fun visit(evaluation: EnumLiteralEvaluation): ReportValue {
            return ReportValue.Text(evaluation.literal.name)
        }

        override fun visit(evaluation: InfinityEvaluation): ReportValue {
            return ReportValue.Text(expressionEvaluationSerializer.serialize(evaluation))
        }

        override fun visit(evaluation: IntegerEvaluation): ReportValue {
            return ReportValue.Integer(evaluation.value.toBigInteger())
        }

        override fun visit(evaluation: NothingEvaluation): ReportValue {
            return ReportValue.Null
        }

        override fun visit(evaluation: RangeEvaluation): ReportValue {
            return ReportValue.Text(expressionEvaluationSerializer.serialize(evaluation))
        }

        override fun visit(evaluation: RealEvaluation): ReportValue {
            return ReportValue.Real(evaluation.value)
        }

        override fun visit(evaluation: StringEvaluation): ReportValue {
            return ReportValue.Text(evaluation.value)
        }

        override fun visit(evaluation: InstanceEvaluation): ReportValue {
            val paths = evaluation.instances.map {
                ReportValue.Text(path(instanceReferenceProvider.getReference(it)))
            }
            if (paths.size == 1) {
                return paths.single()
            }
            return ReportValue.Array(paths)
        }
    }
}
