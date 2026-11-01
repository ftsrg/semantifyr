/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.UnsupportedConstruct
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConeOfInfluenceComputation
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.IntegerRange
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.VariableBoundsComputation
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.VariableBoundsInfo
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableReads
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

// Spin and Uppaal compute in 32-bit integers that wrap, so every integer the property depends on must stay within 32 bits
@VerificationScoped
class IntegerRangeChecker @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val havocValueCollector: HavocValueCollector,
) {

    private val bounds = mutableMapOf<InlinedOxsts, VariableBoundsInfo>()

    // the engines choose a havoced integer among HavocValueCollector's candidates, so the ranges hold for the engine's model
    fun boundsOf(inlinedOxsts: InlinedOxsts): VariableBoundsInfo {
        return bounds.getOrPut(inlinedOxsts) {
            VariableBoundsComputation(inlinedOxsts, MetaConstantExpressionEvaluator(), builtinSymbolResolver) {
                val candidates = havocValueCollector.valuesFor(it)
                IntegerRange(candidates.min().toLong(), candidates.max().toLong())
            }.compute()
        }
    }

    fun check(inlinedOxsts: InlinedOxsts, isChecked: (VariableDeclaration) -> Boolean): List<UnsupportedConstruct> {
        val bounds = boundsOf(inlinedOxsts)
        val evaluator = MetaConstantExpressionEvaluator()
        val cone = ConeOfInfluenceComputation(inlinedOxsts, evaluator).compute()

        val outOfRange = bounds.variableRanges.filter { (variable, range) ->
            cone.isRelevant(variable) && isChecked(variable) && !range.fitsInt()
        }
        val variables = outOfRange.map { (variable, range) ->
            val description = if (range.isBounded) {
                "integer variables beyond the 32-bit range"
            } else {
                "unbounded integer variables"
            }
            UnsupportedConstruct(variable, description)
        }
        val arithmetic = bounds.arithmeticRanges.filterValues {
            !it.fitsInt()
        }.keys.mapNotNull { expression ->
            val reads = expression.variableReads(evaluator).keys
            val relevant = reads.firstOrNull {
                cone.isRelevant(it)
            }
            when {
                reads.any { it in outOfRange } -> null
                reads.isNotEmpty() && relevant == null -> null
                else -> UnsupportedConstruct(relevant ?: expression, "arithmetic that may leave the 32-bit range")
            }
        }
        return variables + arithmetic
    }

    private fun IntegerRange.fitsInt(): Boolean {
        return isWithin(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
    }

}
