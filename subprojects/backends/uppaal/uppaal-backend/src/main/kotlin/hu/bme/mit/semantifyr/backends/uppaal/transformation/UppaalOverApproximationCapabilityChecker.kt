/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.UnsupportedConstruct
import hu.bme.mit.semantifyr.backend.transformation.HavocValueCollector
import hu.bme.mit.semantifyr.backend.transformation.IntegerRangeChecker
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

// a run is exact, over- or under-approximating, never both, and Uppaal havocs an integer among candidate values
class UppaalOverApproximationCapabilityChecker @Inject constructor(
    integerRangeChecker: IntegerRangeChecker,
    uppaalDeclarationsBuilder: UppaalDeclarationsBuilder,
    uppaalVariableTransformer: UppaalVariableTransformer,
    clockTestAnalysis: ClockTestAnalysis,
    constantExpressionEvaluatorProvider: ConstantExpressionEvaluatorProvider,
    private val havocValueCollector: HavocValueCollector,
) : UppaalCapabilityChecker(integerRangeChecker, uppaalDeclarationsBuilder, uppaalVariableTransformer, clockTestAnalysis, constantExpressionEvaluatorProvider) {

    override fun checkModel(inlinedOxsts: InlinedOxsts): List<UnsupportedConstruct> {
        val havoced = inlinedOxsts.eAllOfType<HavocOperation>().map {
            OxstsUtils.requireVariableReference(it.reference)
        }
        val underApproximated = havoced.distinct().filter {
            uppaalVariableTransformer.kindOf(it) == UppaalVariableKind.Integer && !havocValueCollector.isComplete(it)
        }.map {
            UnsupportedConstruct(it, "havoced integers read other than in comparisons with literals")
        }
        return super.checkModel(inlinedOxsts) + underApproximated
    }
}
