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
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

// a run is exact, over- or under-approximating, never both, and Uppaal havocs an integer among candidate values
class UppaalOverApproximationCapabilityChecker @Inject constructor(
    builtinSymbolResolver: BuiltinSymbolResolver,
    builtinAnnotationHandler: BuiltinAnnotationHandler,
    integerRangeChecker: IntegerRangeChecker,
    uppaalDeclarationsBuilder: UppaalDeclarationsBuilder,
    private val havocValueCollector: HavocValueCollector,
) : UppaalCapabilityChecker(builtinSymbolResolver, builtinAnnotationHandler, integerRangeChecker, uppaalDeclarationsBuilder) {

    override fun checkModel(inlinedOxsts: InlinedOxsts): List<UnsupportedConstruct> {
        val havoced = inlinedOxsts.variables.filter {
            it.expression == null
        } + inlinedOxsts.eAllOfType<LocalVarDeclarationOperation>().filter {
            it.expression == null
        } + inlinedOxsts.eAllOfType<HavocOperation>().map {
            OxstsUtils.requireVariableReference(it.reference)
        }
        val underApproximated = havoced.distinct().filter {
            isInteger(it) && !havocValueCollector.isComplete(it)
        }.map {
            UnsupportedConstruct(it, "havoced integers read other than in comparisons with literals")
        }
        return super.checkModel(inlinedOxsts) + underApproximated
    }

    private fun isInteger(variable: VariableDeclaration): Boolean {
        return variable.typeSpecification?.domain === builtinSymbolResolver.intDatatype(variable) && !builtinAnnotationHandler.isClockVariable(variable)
    }
}
