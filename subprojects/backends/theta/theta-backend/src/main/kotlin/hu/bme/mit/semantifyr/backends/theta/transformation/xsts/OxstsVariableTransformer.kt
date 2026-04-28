/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.xsts.lang.xsts.LocalVarDeclOperation
import hu.bme.mit.semantifyr.xsts.lang.xsts.TopLevelVariableDeclaration

private typealias XstsVariableDeclaration = hu.bme.mit.semantifyr.xsts.lang.xsts.VariableDeclaration
typealias XstsTopLevelVariableDeclaration = TopLevelVariableDeclaration
private typealias XstsLocalVarDeclarationOperation = LocalVarDeclOperation

@VerificationScoped
class OxstsVariableTransformer {

    @Inject
    private lateinit var oxstsExpressionTransformer: OxstsExpressionTransformer

    @Inject
    private lateinit var oxstsTypeReferenceTransformer: OxstsTypeReferenceTransformer

    @Inject
    private lateinit var builtinAnnotationHandler: BuiltinAnnotationHandler

    private val topVariableMap = mutableMapOf<VariableDeclaration, XstsTopLevelVariableDeclaration>()
    private val localVariableMap = mutableMapOf<LocalVarDeclarationOperation, XstsLocalVarDeclarationOperation>()

    fun transform(variableDeclaration: VariableDeclaration): XstsVariableDeclaration {
        if (variableDeclaration is LocalVarDeclarationOperation) {
            return transformLocalVar(variableDeclaration)
        }
        return transformTopLevel(variableDeclaration)
    }

    fun transformTopLevel(variableDeclaration: VariableDeclaration): XstsTopLevelVariableDeclaration {
        return topVariableMap.getOrPut(variableDeclaration) {
            val variable = XstsFactory.createTopLevelVariableDeclaration()

            if (builtinAnnotationHandler.isControlVariable(variableDeclaration)) {
                variable.isControl = true
            }

            transformInternals(variable, variableDeclaration)

            variable
        }
    }

    fun transformLocalVar(localVarDeclarationOperation: LocalVarDeclarationOperation): XstsLocalVarDeclarationOperation {
        return localVariableMap.getOrPut(localVarDeclarationOperation) {
            val variable = XstsFactory.createLocalVarDeclOperation()

            transformInternals(variable, localVarDeclarationOperation)

            variable
        }
    }

    private fun transformInternals(variable: XstsVariableDeclaration, variableDeclaration: VariableDeclaration) {
        variable.name = variableDeclaration.name

        val type = variableDeclaration.typeSpecification.domain
        variable.type = oxstsTypeReferenceTransformer.transform(type, variableDeclaration.typeSpecification?.multiplicity)

        if (variableDeclaration.expression != null) {
            variable.expression = oxstsExpressionTransformer.transform(variableDeclaration.expression)
        }
    }
}
