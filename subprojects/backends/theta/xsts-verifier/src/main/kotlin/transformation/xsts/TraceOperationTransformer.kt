/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts

import hu.bme.mit.semantifyr.oxsts.model.oxsts.TraceOperation
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.utils.copy
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel

@CompilationScoped
class TraceOperationTransformer {

    private val traceOperationMap = mutableMapOf<XstsTopLevelVariableDeclaration, TraceOperation>()

    private val tracerVariables = mutableListOf<XstsTopLevelVariableDeclaration>()

    private fun createTracerVariable(traceOperation: TraceOperation): XstsTopLevelVariableDeclaration {
        val tracerVariable = XstsFactory.createTopLevelVariableDeclaration().also {
            it.isControl = true
            it.name = traceOperation.name
            it.type = XstsFactory.createBooleanType()
            it.expression = XstsFactory.createLiteralBoolean().also {
                it.isValue = false
            }
        }

        traceOperationMap[tracerVariable] = traceOperation
        tracerVariables += tracerVariable

        return tracerVariable
    }

    private fun assignTracerVariable(tracerVariable: XstsTopLevelVariableDeclaration, value: Boolean): XstsOperation {
        return XstsFactory.createAssignmentOperation().also {
            it.reference = XstsFactory.createElementReferenceExpression().also {
                it.element = tracerVariable
            }
            it.expression = XstsFactory.createLiteralBoolean().also {
                it.isValue = value
            }
        }
    }

    fun finalizeTransformedTraceOperations(xstsModel: XstsModel) {
        for (tracerVariable in tracerVariables) {
            xstsModel.variableDeclarations += tracerVariable

            val resetTracerVariable = assignTracerVariable(tracerVariable, false)
            for (branch in xstsModel.tran.branches) {
                branch.steps.addFirst(resetTracerVariable.copy())
            }
        }
    }

    fun transformTraceOperation(traceOperation: TraceOperation): XstsOperation {
        val tracerVariable = createTracerVariable(traceOperation)

        return assignTracerVariable(tracerVariable, true)
    }

    fun isTracerVariable(variableDeclaration: XstsTopLevelVariableDeclaration): Boolean {
        return variableDeclaration in tracerVariables
    }

    fun getTraceOperation(tracerVariable: XstsTopLevelVariableDeclaration): TraceOperation {
        return traceOperationMap[tracerVariable] ?: error("No corresponding trace operation found!")
    }

}
