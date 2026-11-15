/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.isExactJsonInteger
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluationVisitor
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ExpressionEvaluationSerializer
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification
import hu.bme.mit.semantifyr.verifier.ReportType
import hu.bme.mit.semantifyr.verifier.ReportTypeKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigInteger

class ReportValueConverter @Inject constructor(
    private val oxstsQualifiedNameProvider: OxstsQualifiedNameProvider,
    private val instanceReferenceProvider: InstanceReferenceProvider,
    private val expressionSerializer: ExpressionSerializer,
    private val expressionEvaluationSerializer: ExpressionEvaluationSerializer,
    private val multiplicityRangeEvaluator: MultiplicityRangeEvaluator,
) {

    fun integer(value: BigInteger): JsonElement {
        if (isExactJsonInteger(value)) {
            return JsonPrimitive(value.toLong())
        }
        return JsonPrimitive(value.toString())
    }

    fun fraction(value: Fraction): JsonElement {
        if (value.isWhole) {
            return integer(value.numerator)
        }
        return JsonPrimitive(value.toString())
    }

    fun path(expression: Expression): String {
        return expressionSerializer.serialize(expression)
    }

    fun path(evaluation: InstanceEvaluation): String {
        return expressionEvaluationSerializer.serialize(evaluation)
    }

    fun value(expression: Expression): JsonElement {
        return when (expression) {
            is LiteralInteger -> integer(expression.value.toBigInteger())
            is LiteralBoolean -> JsonPrimitive(expression.isValue)
            is LiteralReal -> JsonPrimitive(expression.value)
            is LiteralString -> JsonPrimitive(expression.value)
            is LiteralNothing -> JsonNull
            is ArrayLiteral -> JsonArray(
                expression.values.map {
                    value(it)
                },
            )
            is RecordLiteral -> JsonObject(
                expression.fields.associate {
                    it.field.name to value(it.expression)
                },
            )
            else -> reference(expression)
        }
    }

    private fun reference(expression: Expression): JsonElement {
        val literal = (expression as? ElementReference)?.element as? EnumLiteral
        if (literal != null) {
            return JsonPrimitive(literal.name)
        }
        return JsonPrimitive(path(expression))
    }

    fun value(evaluation: ExpressionEvaluation): JsonElement {
        return evaluationConverter.convert(evaluation)
    }

    fun type(typeSpecification: TypeSpecification?): ReportType {
        val element = elementType(typeSpecification)
        if (typeSpecification?.multiplicity == null) {
            return element
        }
        val upperBound = multiplicityRangeEvaluator.evaluate(typeSpecification).upperBound
        return ReportType(
            kind = ReportTypeKind.Array,
            element = element,
            size = upperBound.takeIf {
                it != RangeEvaluation.INFINITY
            }?.toInt(),
        )
    }

    fun elementType(typeSpecification: TypeSpecification?): ReportType {
        val domain = typeSpecification?.domain ?: error("a traced value has no declared type")
        val kind = when (domain) {
            is DataTypeDeclaration -> ReportTypeKind.Datatype
            is EnumDeclaration -> ReportTypeKind.Enum
            is ClassDeclaration -> ReportTypeKind.Class
            is RecordDeclaration -> ReportTypeKind.Record
            else -> error("a traced value has the type ${domain::class.simpleName}, which a report cannot name")
        }
        return ReportType(
            kind = kind,
            name = oxstsQualifiedNameProvider.getFullyQualifiedNameString(domain),
        )
    }

    private val evaluationConverter = EvaluationConverter()

    private inner class EvaluationConverter : CompileTimeExpressionEvaluationVisitor<JsonElement>() {
        fun convert(evaluation: ExpressionEvaluation): JsonElement {
            return visit(evaluation)
        }

        override fun visit(evaluation: ArrayEvaluation): JsonElement {
            return JsonArray(
                evaluation.elements.map {
                    visit(it)
                },
            )
        }

        override fun visit(evaluation: BooleanEvaluation): JsonElement {
            return JsonPrimitive(evaluation.value)
        }

        override fun visit(evaluation: EnumLiteralEvaluation): JsonElement {
            return JsonPrimitive(evaluation.literal.name)
        }

        override fun visit(evaluation: InfinityEvaluation): JsonElement {
            return JsonPrimitive(expressionEvaluationSerializer.serialize(evaluation))
        }

        override fun visit(evaluation: IntegerEvaluation): JsonElement {
            return integer(evaluation.value.toBigInteger())
        }

        override fun visit(evaluation: NothingEvaluation): JsonElement {
            return JsonNull
        }

        override fun visit(evaluation: RangeEvaluation): JsonElement {
            return JsonPrimitive(expressionEvaluationSerializer.serialize(evaluation))
        }

        override fun visit(evaluation: RealEvaluation): JsonElement {
            return JsonPrimitive(evaluation.value)
        }

        override fun visit(evaluation: StringEvaluation): JsonElement {
            return JsonPrimitive(evaluation.value)
        }

        override fun visit(evaluation: InstanceEvaluation): JsonElement {
            val paths = evaluation.instances.map {
                JsonPrimitive(path(instanceReferenceProvider.getReference(it)))
            }
            if (paths.size == 1) {
                return paths.single()
            }
            return JsonArray(paths)
        }
    }
}
