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
        return integerRangeChecker.checkArithmetic(inlinedOxsts, Limitation.ArithmeticBeyond32Bits) {
            uppaalDeclarationsBuilder.declaredRangeOf(it, inlinedOxsts)
        }
    }

    override fun describeUnsupported(element: EObject): Limitation? {
        return when (element) {
            is VariableDeclaration -> describeUnsupportedType(element)
            is AssignmentOperation -> describeUnsupportedClockReset(element)
            is HavocOperation -> describeUnsupportedClockHavoc(element)
            is ForOperation -> Limitation.ForOperations
            is LiteralString -> Limitation.StringLiterals
            is ArrayLiteral -> Limitation.ArrayLiterals
            is IndexingSuffixExpression -> Limitation.ArrayIndexing
            else -> null
        }
    }

    private fun describeUnsupportedType(variable: VariableDeclaration): Limitation? {
        val domain = variable.typeSpecification?.domain ?: return null
        if (builtinAnnotationHandler.isClockVariable(variable)) {
            if (domain is DataTypeDeclaration) {
                return null
            }
            return Limitation.NonDataTypedClocks
        }
        return when (domain) {
            builtinSymbolResolver.realDatatype(variable) -> Limitation.RealVariables
            builtinSymbolResolver.stringDatatype(variable) -> Limitation.StringVariables
            else -> null
        }
    }

    private fun describeUnsupportedClockReset(operation: AssignmentOperation): Limitation? {
        if (!isClockReference(operation.reference)) {
            return null
        }
        if ((operation.expression as? LiteralInteger)?.value == 0) {
            return null
        }
        return Limitation.NonZeroClockResets
    }

    private fun describeUnsupportedClockHavoc(operation: HavocOperation): Limitation? {
        if (!isClockReference(operation.reference)) {
            return null
        }
        return Limitation.ClockHavocs
    }

    private fun isClockReference(reference: Expression): Boolean {
        val variable = (reference as? ElementReference)?.element as? VariableDeclaration ?: return false
        return builtinAnnotationHandler.isClockVariable(variable)
    }

    enum class Limitation(
        override val construct: String,
        override val reason: String,
    ) : BackendLimitation {
        RealVariables("variables of type real", "the Uppaal translation declares integer, boolean, enumeration and clock variables only"),
        StringVariables("variables of type string", "the Uppaal translation declares integer, boolean, enumeration and clock variables only"),
        NonDataTypedClocks("@Clock on variables that are not data-typed", "a clock stands for a time value, which an enumeration cannot hold"),
        NonZeroClockResets("clock resets to values other than 0", "the translation resets a clock to 0 only"),
        ClockHavocs("havoc on clocks", "a clock cannot take an arbitrary value"),
        ForOperations("for-operations", "the Uppaal translation has no loop"),
        StringLiterals("string literals", "Uppaal has no strings"),
        ArrayLiterals("array literals", "the backend receives arrays split into one variable per slot and has no form for an array value"),
        ArrayIndexing("array indexing", "the backend receives arrays split into one variable per slot and has no form for indexing"),
        ArithmeticBeyond32Bits("arithmetic that may leave the 32-bit range", "Uppaal's arithmetic wraps at 32 bits before its range check can stop the run; the property depends on the arithmetic"),
    }
}
