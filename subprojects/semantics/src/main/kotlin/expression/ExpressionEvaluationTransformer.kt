/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory

abstract class ExpressionEvaluationTransformer : StaticExpressionEvaluationVisitor<Expression>() {

    fun transformEvaluation(evaluation: ExpressionEvaluation): Expression {
        return visit(evaluation)
    }

}

@CompilationScoped
open class ConstantExpressionEvaluationTransformer : ExpressionEvaluationTransformer() {

    override fun visit(evaluation: ArrayEvaluation): Expression {
        return OxstsFactory.createArrayLiteral().also {
            it.values += evaluation.elements.map {
                visit(it)
            }
        }
    }

    override fun visit(evaluation: BooleanEvaluation): Expression {
        return OxstsFactory.createLiteralBoolean(evaluation.value)
    }

    override fun visit(evaluation: EnumLiteralEvaluation): Expression {
        return OxstsFactory.createElementReference(evaluation.literal)
    }

    override fun visit(evaluation: InfinityEvaluation): Expression {
        return OxstsFactory.createLiteralInfinity()
    }

    override fun visit(evaluation: IntegerEvaluation): Expression {
        return OxstsFactory.createLiteralInteger(evaluation.value)
    }

    override fun visit(evaluation: NothingEvaluation): Expression {
        return OxstsFactory.createLiteralNothing()
    }

    override fun visit(evaluation: RangeEvaluation): Expression {
        return OxstsFactory.createRangeExpression().also {
            // FIXME: should not be integer!
            it.left = OxstsFactory.createLiteralInteger(evaluation.lowerBound)
            it.right = OxstsFactory.createLiteralInteger(evaluation.upperBound)
        }
    }

    override fun visit(evaluation: RealEvaluation): Expression {
        return OxstsFactory.createLiteralReal().also {
            it.value = evaluation.value
        }
    }

    override fun visit(evaluation: StringEvaluation): Expression {
        return OxstsFactory.createLiteralString().also {
            it.value = evaluation.value
        }
    }

    override fun visit(evaluation: InstanceEvaluation): Expression {
        error("Instance evaluations are not constant!")
    }

}

@CompilationScoped
class DeflatedExpressionEvaluationTransformer : ConstantExpressionEvaluationTransformer() {

    private var instanceId = 0
    private val instanceIds = mutableMapOf<Instance, Int>()
    private val idInstance = mutableMapOf<Int, Instance>()

    override fun visit(evaluation: InstanceEvaluation): Expression {
        val instance = evaluation.instances.single()
        val id = instanceIds.getOrPut(instance) {
            instanceId++
        }
        idInstance[id] = instance
        return OxstsFactory.createLiteralInteger(id)
    }

    fun instanceOfId(id: Int): Instance {
        return idInstance[id] ?: error("Unknown instance id!")
    }

}

@CompilationScoped
class StaticExpressionEvaluationTransformer : ConstantExpressionEvaluationTransformer() {

    @Inject
    private lateinit var instanceReferenceProvider: InstanceReferenceProvider

    override fun visit(evaluation: InstanceEvaluation): Expression {
        if (evaluation.instances.size == 1) {
            return instanceReferenceProvider.getReference(evaluation.instances.single())
        }

        return OxstsFactory.createArrayLiteral().also {
            for (instance in evaluation.instances) {
                it.values += instanceReferenceProvider.getReference(instance)
            }
        }
    }

}
