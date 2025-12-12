/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
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
class DeflatedEvaluationTransformer : ConstantEvaluationTransformer() {

    private var instanceId = 0
    private val instanceIds = mutableMapOf<Instance, Int>()
    private val idInstance = mutableMapOf<Int, Instance>()

    override fun transformEvaluation(evaluation: InstanceEvaluation): Expression {
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
class StaticEvaluationTransformer : ConstantEvaluationTransformer() {

    @Inject
    private lateinit var instanceReferenceProvider: InstanceReferenceProvider

    override fun transformEvaluation(evaluation: InstanceEvaluation): Expression {
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
