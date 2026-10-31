/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.expression.evaluateTyped
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.scopes.CompilationScoped
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class ArgumentTrace(
    val parameterDeclaration: ParameterDeclaration,
    val evaluation: ExpressionEvaluation,
)

class TransitionCallTrace(
    val self: InstanceEvaluation,
    val transitionDeclaration: TransitionDeclaration,
    val arguments: List<ArgumentTrace>,
    val parent: TransitionCallTrace?,
)

@CompilationScoped
class TransitionCallTraceTransformer @Inject constructor(
    private val compileTimeExpressionEvaluatorProvider: CompileTimeExpressionEvaluatorProvider,
    private val builtinSymbolResolver: BuiltinSymbolResolver,
) {

    private var tracerCount = 0
    private val tracerAssignments = mutableMapOf<AssignmentOperation, TransitionCallTrace>()

    // inlined sequences are spliced into their parent, so the enclosing call is recorded per pending inline operation
    private val enclosingTraces = mutableMapOf<InlineOperation, TransitionCallTrace>()

    fun traceTransitionCall(
        instance: Instance,
        containerInstance: Instance,
        transitionDeclaration: TransitionDeclaration,
        callExpression: CallSuffixExpression,
        body: Operation,
    ): AssignmentOperation {
        val tracerVariable = createTracerVariable(transitionDeclaration)
        val parent = findEnclosingTracer(callExpression)

        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)
        val transitionCallTrace = TransitionCallTrace(
            self = InstanceEvaluation(containerInstance),
            transitionDeclaration = transitionDeclaration,
            arguments = transitionDeclaration.parameters.mapIndexedNotNull { index, parameter ->
                OxstsUtils.getArgumentFor(callExpression.arguments, parameter, index)?.let {
                    ArgumentTrace(
                        parameter,
                        evaluator.evaluateTyped(ExpressionEvaluation::class.java, it.expression),
                    )
                }
            },
            parent = parent,
        )
        enclose(body, transitionCallTrace)

        val tracerAssignment = createTracerAssignment(tracerVariable, value = true)
        tracerAssignments[tracerAssignment] = transitionCallTrace
        return tracerAssignment
    }

    fun inheritEnclosingTrace(operation: InlineOperation, expansion: Operation) {
        val enclosingTrace = enclosingTraces.remove(operation) ?: return
        enclose(expansion, enclosingTrace)
    }

    fun finalize(inlinedOxsts: InlinedOxsts): TransitionCallTraceMap {
        val tracerVariables = tracerAssignments.keys.map {
            it.tracerVariable
        }
        if (tracerVariables.isNotEmpty()) {
            inlinedOxsts.variables += tracerVariables
            for (branch in inlinedOxsts.mainTransition.branches) {
                for (variable in tracerVariables.reversed()) {
                    branch.steps.addFirst(createTracerAssignment(variable, value = false))
                }
            }
        }
        return TransitionCallTraceMap(
            tracerAssignments.entries.associate {
                it.key.tracerVariable to it.value
            },
        )
    }

    private fun createTracerVariable(context: EObject): VariableDeclaration {
        return OxstsFactory.createVariableDeclaration().also {
            it.name = TransitionTracerNames.tracerName(tracerCount++)
            it.typeSpecification = OxstsFactory.createTypeSpecification().also {
                it.domain = builtinSymbolResolver.boolDatatype(context)
            }
            it.expression = OxstsFactory.createLiteralBoolean(false)
            it.annotation = OxstsFactory.createAnnotationContainer().also {
                it.annotations += OxstsFactory.createAnnotation().also {
                    it.declaration = builtinSymbolResolver.controlAnnotation(context)
                }
                it.annotations += OxstsFactory.createAnnotation().also {
                    it.declaration = builtinSymbolResolver.nonOptimizableAnnotation(context)
                }
            }
        }
    }

    private fun createTracerAssignment(variable: VariableDeclaration, value: Boolean): AssignmentOperation {
        return OxstsFactory.createAssignmentOperation().also {
            it.reference = OxstsFactory.createElementReference(variable)
            it.expression = OxstsFactory.createLiteralBoolean(value)
        }
    }

    private fun enclose(operation: Operation, trace: TransitionCallTrace) {
        for (inlineOperation in operation.eAllOfType<InlineOperation>()) {
            enclosingTraces.putIfAbsent(inlineOperation, trace)
        }
    }

    private fun findEnclosingTracer(callExpression: CallSuffixExpression): TransitionCallTrace? {
        val inlineOperation = EcoreUtil2.getContainerOfType(callExpression, InlineOperation::class.java) ?: return null
        return enclosingTraces[inlineOperation]
    }

    private val AssignmentOperation.tracerVariable: VariableDeclaration
        get() = (reference as ElementReference).element as VariableDeclaration

}
