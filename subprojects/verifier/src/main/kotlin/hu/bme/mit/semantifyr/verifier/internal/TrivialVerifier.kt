/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.internal

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.BooleanEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import kotlin.time.Instant

const val TRIVIAL_BACKEND_ID: String = "trivial"

class TrivialVerifier @Inject constructor(
    private val constantExpressionEvaluatorProvider: ConstantExpressionEvaluatorProvider,
) {

    private val logger by loggerFactory()

    fun isTriviallyVerifiable(inlinedOxsts: InlinedOxsts): Boolean {
        return inlinedOxsts.variables.isEmpty() &&
            inlinedOxsts.initTransition.isEmpty() &&
            inlinedOxsts.mainTransition.isEmpty()
    }

    fun verifyTrivially(inlinedOxsts: InlinedOxsts, startedAt: Instant): BackendVerificationResult {
        val body = when (val expression = inlinedOxsts.property.expression) {
            is AG -> expression.body
            is EF -> expression.body
            else -> error("Specified property is not in the expected form")
        }

        val evaluation = constantExpressionEvaluatorProvider.evaluate(body)
        require(evaluation is BooleanEvaluation) {
            "Trivially verifiable model's property body did not evaluate to a boolean (got ${evaluation::class.simpleName})"
        }

        val verdict = if (evaluation.value()) {
            VerificationVerdict.Passed
        } else {
            VerificationVerdict.Failed
        }

        logger.info { "Trivial $verdict verdict (model optimized away)." }

        return BackendVerificationResult(
            metadata = VerificationMetadata(
                backendId = TRIVIAL_BACKEND_ID,
                startedAt = startedAt,
            ),
            verdict = verdict,
            metrics = BackendMetrics(),
            inlinedWitness = trivialWitness(inlinedOxsts),
            message = "Property decided at compile time.",
        )
    }

    private fun trivialWitness(inlinedOxsts: InlinedOxsts): InlinedWitness {
        val initialState = WitnessState(values = emptyList())
        val initializedState = WitnessState(values = emptyList())
        val transitionState = WitnessState(values = emptyList())
        return InlinedWitness(
            initialState = initialState,
            initializedState = initializedState,
            transitionStates = listOf(transitionState),
            nextStateMap = mapOf(initializedState to listOf(transitionState)),
            inlinedOxsts = inlinedOxsts,
        )
    }

    private fun TransitionDeclaration.isEmpty(): Boolean {
        if (branches.size > 1) {
            return false
        }
        val branch = branches.single()
        return branch.steps.isEmpty()
    }
}
