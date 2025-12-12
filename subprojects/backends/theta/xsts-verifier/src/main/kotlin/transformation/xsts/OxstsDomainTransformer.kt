/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

typealias XstsType = hu.bme.mit.semantifyr.xsts.lang.xsts.Type
private typealias XstsEnumDeclaration = hu.bme.mit.semantifyr.xsts.lang.xsts.EnumDeclaration
private typealias XstsEnumLiteral = hu.bme.mit.semantifyr.xsts.lang.xsts.EnumLiteral

@CompilationScoped
class OxstsDomainTransformer {

    @Inject
    private lateinit var builtinSymbolResolver: BuiltinSymbolResolver

    private val enumDeclarationMap = mutableMapOf<EnumDeclaration, XstsEnumDeclaration>()
    private val enumLiteralMap = mutableMapOf<EnumLiteral, XstsEnumLiteral>()

    fun transform(enumDeclaration: EnumDeclaration): XstsEnumDeclaration {
        return enumDeclarationMap.getOrPut(enumDeclaration) {
            XstsFactory.createEnumDeclaration().also {
                it.name = enumDeclaration.name

                for (literal in enumDeclaration.literals) {
                    it.literals += transform(literal)
                }
            }
        }
    }

    fun transform(enumLiteral: EnumLiteral): XstsEnumLiteral {
        return enumLiteralMap.getOrPut(enumLiteral) {
            XstsFactory.createEnumLiteral().also {
                it.name = enumLiteral.name
            }
        }
    }

    fun transform(dataTypeDeclaration: DataTypeDeclaration): XstsType {
        return when (dataTypeDeclaration) {
            builtinSymbolResolver.intDatatype(dataTypeDeclaration) -> XstsFactory.createIntegerType()
            builtinSymbolResolver.boolDatatype(dataTypeDeclaration) -> XstsFactory.createBooleanType()
            else -> error("Unexpected data type: $this")
        }
    }

    fun getOriginal(enumLiteral: XstsEnumLiteral): EnumLiteral {
        return enumLiteralMap.entries.firstOrNull { (_, value) ->
            value == enumLiteral
        }?.key ?: error("Corresponding enum literal was not transformed!")
    }

}
