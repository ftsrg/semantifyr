/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendLimitation
import hu.bme.mit.semantifyr.backend.UnsupportedConstruct
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConeOfInfluenceComputation
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConeOfInfluenceInfo
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

    private fun boundsOf(inlinedOxsts: InlinedOxsts): VariableBoundsInfo {
        return bounds.getOrPut(inlinedOxsts) {
            computeBounds(inlinedOxsts) {
                IntegerRange.UNBOUNDED
            }
        }
    }

    fun rangeOf(variable: VariableDeclaration, inlinedOxsts: InlinedOxsts): IntegerRange? {
        return boundsOf(inlinedOxsts).variableRanges[variable]?.takeIf {
            it.fitsInt()
        }
    }

    fun check(
        inlinedOxsts: InlinedOxsts,
        unboundedVariables: BackendLimitation,
        variablesBeyondRange: BackendLimitation,
        arithmeticBeyondRange: BackendLimitation,
    ): List<UnsupportedConstruct> {
        val bounds = boundsOf(inlinedOxsts)
        val cone = coneOf(inlinedOxsts)

        val outOfRange = bounds.variableRanges.filter { (variable, range) ->
            cone.isRelevant(variable) && !range.fitsInt()
        }
        val variables = outOfRange.map { (variable, range) ->
            val limitation = if (range.isBounded) {
                variablesBeyondRange
            } else {
                unboundedVariables
            }
            UnsupportedConstruct(variable, limitation)
        }
        return variables + leavingArithmetic(bounds, cone, outOfRange.keys, arithmeticBeyondRange)
    }

    // Uppaal stops on an assignment outside a variable's declared range, so only its arithmetic can wrap
    fun checkArithmetic(
        inlinedOxsts: InlinedOxsts,
        arithmeticBeyondRange: BackendLimitation,
        declaredRange: (VariableDeclaration) -> IntegerRange,
    ): List<UnsupportedConstruct> {
        val bounds = computeBounds(inlinedOxsts, declaredRange)
        return leavingArithmetic(bounds, coneOf(inlinedOxsts), emptySet(), arithmeticBeyondRange)
    }

    // the engines choose a havoced integer among HavocValueCollector's candidates, so the ranges hold for the engine's model
    private fun computeBounds(inlinedOxsts: InlinedOxsts, limit: (VariableDeclaration) -> IntegerRange): VariableBoundsInfo {
        return VariableBoundsComputation(
            inlinedOxsts = inlinedOxsts,
            evaluator = MetaConstantExpressionEvaluator(),
            builtinSymbolResolver = builtinSymbolResolver,
            havocRange = {
                val candidates = havocValueCollector.valuesFor(it)
                IntegerRange(candidates.min().toLong(), candidates.max().toLong())
            },
            limit = limit,
        ).compute()
    }

    private fun coneOf(inlinedOxsts: InlinedOxsts): ConeOfInfluenceInfo {
        return ConeOfInfluenceComputation(inlinedOxsts, MetaConstantExpressionEvaluator()).compute()
    }

    private fun leavingArithmetic(
        bounds: VariableBoundsInfo,
        cone: ConeOfInfluenceInfo,
        refused: Set<VariableDeclaration>,
        arithmeticBeyondRange: BackendLimitation,
    ): List<UnsupportedConstruct> {
        val evaluator = MetaConstantExpressionEvaluator()
        return bounds.arithmeticRanges.filterValues {
            !it.fitsInt()
        }.keys.mapNotNull { expression ->
            val reads = expression.variableReads(evaluator).keys
            val relevant = reads.firstOrNull {
                cone.isRelevant(it)
            }
            when {
                reads.any { it in refused } -> null
                reads.isNotEmpty() && relevant == null -> null
                else -> UnsupportedConstruct(relevant ?: expression, arithmeticBeyondRange)
            }
        }
    }

    private fun IntegerRange.fitsInt(): Boolean {
        return isWithin(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
    }

}
