/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.BooleanEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory

abstract class EvaluationTransformer {

    fun transformEvaluation(evaluation: ExpressionEvaluation): Expression {
        return when (evaluation) {
            is BooleanEvaluation -> transformEvaluation(evaluation)
            is IntegerEvaluation -> transformEvaluation(evaluation)
            is InstanceEvaluation -> transformEvaluation(evaluation)
            else -> error("Unsupported evaluation!")
        }
    }

    protected abstract fun transformEvaluation(evaluation: BooleanEvaluation): Expression
    protected abstract fun transformEvaluation(evaluation: IntegerEvaluation): Expression
    protected abstract fun transformEvaluation(evaluation: InstanceEvaluation): Expression

}

@CompilationScoped
open class ConstantEvaluationTransformer : EvaluationTransformer() {

    override fun transformEvaluation(evaluation: BooleanEvaluation): Expression {
        return OxstsFactory.createLiteralBoolean(evaluation.value)
    }

    override fun transformEvaluation(evaluation: IntegerEvaluation): Expression {
        return OxstsFactory.createLiteralInteger(evaluation.value)
    }

    override fun transformEvaluation(evaluation: InstanceEvaluation): Expression {
        error("Instance evaluations are not constant!")
    }

}

@CompilationScoped
class StaticEvaluationTransformer : ConstantEvaluationTransformer() {

    private var instanceId = 0
    private val instanceIds = mutableMapOf<Instance, Int>()

    override fun transformEvaluation(evaluation: InstanceEvaluation): Expression {
        val instance = evaluation.instances.single()
        val id = instanceIds.getOrPut(instance) {
            instanceId++
        }
        return OxstsFactory.createLiteralInteger(id)
    }

}
