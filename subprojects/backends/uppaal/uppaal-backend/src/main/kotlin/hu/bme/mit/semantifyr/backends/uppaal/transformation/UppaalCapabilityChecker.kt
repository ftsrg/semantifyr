/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.BackendLimitation
import hu.bme.mit.semantifyr.backend.UnsupportedConstruct
import hu.bme.mit.semantifyr.backend.transformation.IntegerRangeChecker
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

open class UppaalCapabilityChecker @Inject constructor(
    protected val builtinSymbolResolver: BuiltinSymbolResolver,
    protected val builtinAnnotationHandler: BuiltinAnnotationHandler,
    private val integerRangeChecker: IntegerRangeChecker,
    private val uppaalDeclarationsBuilder: UppaalDeclarationsBuilder,
) : BackendCapabilityChecker() {

    override fun checkModel(inlinedOxsts: InlinedOxsts): List<UnsupportedConstruct> {
        return integerRangeChecker.checkArithmetic(inlinedOxsts) {
            uppaalDeclarationsBuilder.declaredRangeOf(it, inlinedOxsts)
        }
    }

    override fun describeUnsupported(element: EObject): String? {
        return when (element) {
            is VariableDeclaration -> describeUnsupportedType(element)
            is AssignmentOperation -> describeUnsupportedClockReset(element)
            is HavocOperation -> describeUnsupportedClockHavoc(element)
            is ForOperation -> "for-operations"
            is LiteralString -> "string literals"
            is ArrayLiteral -> "array literals"
            is IndexingSuffixExpression -> "array indexing"
            else -> null
        }
    }

    private fun describeUnsupportedType(variable: VariableDeclaration): String? {
        val domain = variable.typeSpecification?.domain ?: return null
        if (builtinAnnotationHandler.isClockVariable(variable)) {
            if (domain is DataTypeDeclaration) {
                return null
            }
            return "@Clock on variables that are not data-typed"
        }
        if (domain is EnumDeclaration || domain === builtinSymbolResolver.intDatatype(variable) || domain === builtinSymbolResolver.boolDatatype(variable)) {
            return null
        }
        return "variables of type ${domain.name}"
    }

    private fun describeUnsupportedClockReset(operation: AssignmentOperation): String? {
        if (!isClockReference(operation.reference)) {
            return null
        }
        if ((operation.expression as? LiteralInteger)?.value == 0) {
            return null
        }
        return "clock resets to values other than 0"
    }

    private fun describeUnsupportedClockHavoc(operation: HavocOperation): String? {
        if (!isClockReference(operation.reference)) {
            return null
        }
        return "havoc on clocks"
    }

    private fun isClockReference(reference: Expression): Boolean {
        val variable = (reference as? ElementReference)?.element as? VariableDeclaration ?: return false
        return builtinAnnotationHandler.isClockVariable(variable)
    }

    companion object {
        val limitations = listOf(
            BackendLimitation("variables of type real", "the Uppaal translation declares integer, boolean, enumeration and clock variables only"),
            BackendLimitation("variables of type string", "the Uppaal translation declares integer, boolean, enumeration and clock variables only"),
            BackendLimitation("@Clock on variables that are not data-typed", "a clock stands for a time value, which an enumeration cannot hold"),
            BackendLimitation("clock resets to values other than 0", "the translation resets a clock to 0 only"),
            BackendLimitation("havoc on clocks", "a clock cannot take an arbitrary value"),
            BackendLimitation("for-operations", "the Uppaal translation has no loop"),
            BackendLimitation("string literals", "Uppaal has no strings"),
            BackendLimitation("array literals", "the backend receives arrays split into one variable per slot and has no form for an array value"),
            BackendLimitation("array indexing", "the backend receives arrays split into one variable per slot and has no form for indexing"),
            BackendLimitation("arithmetic that may leave the 32-bit range", "Uppaal's arithmetic wraps at 32 bits before its range check can stop the run; the property depends on the arithmetic"),
            BackendLimitation(
                UppaalOverApproximationCapabilityChecker.UNDER_APPROXIMATED_HAVOC,
                "Uppaal chooses a havoced integer among candidate values, which under-approximates a variable read this way, and a run is never both over- and under-approximating",
                configuration = "over-approximation",
            ),
        )
    }
}
