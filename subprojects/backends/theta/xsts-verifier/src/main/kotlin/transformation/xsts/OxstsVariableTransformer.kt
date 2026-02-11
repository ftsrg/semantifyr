/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

private typealias XstsVariableDeclaration = hu.bme.mit.semantifyr.xsts.lang.xsts.VariableDeclaration
private typealias XstsTopLevelVariableDeclaration = hu.bme.mit.semantifyr.xsts.lang.xsts.TopLevelVariableDeclaration
private typealias XstsLocalVarDeclarationOperation = hu.bme.mit.semantifyr.xsts.lang.xsts.LocalVarDeclOperation

@CompilationScoped
class OxstsVariableTransformer {

    @Inject
    private lateinit var oxstsExpressionTransformer: OxstsExpressionTransformer

    @Inject
    private lateinit var oxstsTypeReferenceTransformer: OxstsTypeReferenceTransformer

    @Inject
    private lateinit var builtinAnnotationHandler: BuiltinAnnotationHandler

    @Inject
    private lateinit var expressionTypeEvaluatorProvider: ExpressionTypeEvaluatorProvider

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

        val type = variableDeclaration.typeSpecification?.domain ?: evaluateExpressionType(variableDeclaration)
        variable.type = oxstsTypeReferenceTransformer.transform(type, variableDeclaration.typeSpecification?.multiplicity)

        if (variableDeclaration.expression != null) {
            variable.expression = oxstsExpressionTransformer.transform(variableDeclaration.expression)
        }
    }

    private fun evaluateExpressionType(variableDeclaration: VariableDeclaration): DomainDeclaration {
        val typeEvaluation = expressionTypeEvaluatorProvider.evaluate(variableDeclaration.expression)
        return typeEvaluation.domain
    }

}
