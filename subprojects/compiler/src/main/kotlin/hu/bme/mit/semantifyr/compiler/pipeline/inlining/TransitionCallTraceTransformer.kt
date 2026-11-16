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
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.compiler.scopes.CompilationScoped
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.EcoreUtil2

class ArgumentTrace(
    val parameterDeclaration: ParameterDeclaration,
    val evaluation: ExpressionEvaluation,
)

class RecordedArgumentTrace(
    val parameterDeclaration: ParameterDeclaration,
    val variable: VariableDeclaration,
)

class TransitionCallTrace(
    val self: InstanceEvaluation,
    val transitionDeclaration: TransitionDeclaration,
    val arguments: List<ArgumentTrace>,
    val recordedArguments: List<RecordedArgumentTrace>,
    val parent: TransitionCallTrace?,
)

class TracedCall(
    val tracerAssignment: AssignmentOperation,
    val argumentRecords: List<AssignmentOperation>,
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
        runtimeArguments: Map<ParameterDeclaration, LocalVarDeclarationOperation>,
    ): TracedCall {
        val tracerIndex = tracerCount++
        val tracerVariable = createTracerVariable(tracerIndex, transitionDeclaration)
        val parent = findEnclosingTracer(callExpression)

        val recordedArguments = runtimeArguments.map { (parameter, temporary) ->
            RecordedArgumentTrace(parameter, createArgumentVariable(tracerIndex, parameter, temporary))
        }

        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)
        val transitionCallTrace = TransitionCallTrace(
            self = InstanceEvaluation(containerInstance),
            transitionDeclaration = transitionDeclaration,
            arguments = transitionDeclaration.parameters.filter {
                it !in runtimeArguments
            }.mapNotNull { parameter ->
                val index = transitionDeclaration.parameters.indexOf(parameter)
                OxstsUtils.getArgumentFor(callExpression.arguments, parameter, index)?.let {
                    val evaluation = try {
                        evaluator.evaluateTyped(ExpressionEvaluation::class.java, it.expression)
                    } catch (_: EvaluationFailureException) {
                        sourceError(it.expression, "The argument of traced transition '${transitionDeclaration.name}' is not evaluable at compile time")
                    }
                    ArgumentTrace(
                        parameter,
                        evaluation,
                    )
                }
            },
            recordedArguments = recordedArguments,
            parent = parent,
        )
        enclose(body, transitionCallTrace)

        val tracerAssignment = createTracerAssignment(tracerVariable, value = true)
        tracerAssignments[tracerAssignment] = transitionCallTrace
        return TracedCall(
            tracerAssignment,
            recordedArguments.map {
                OxstsFactory.createAssignmentOperation().also { record ->
                    record.reference = OxstsFactory.createElementReference(it.variable)
                    record.expression = OxstsFactory.createElementReference(runtimeArguments.getValue(it.parameterDeclaration))
                }
            },
        )
    }

    fun inheritEnclosingTrace(operation: InlineOperation, expansion: Operation) {
        val enclosingTrace = enclosingTraces.remove(operation) ?: return
        enclose(expansion, enclosingTrace)
    }

    fun declareTracers(inlinedOxsts: InlinedOxsts) {
        val tracerVariables = tracerAssignments.entries.flatMap { (assignment, trace) ->
            listOf(assignment.assignedVariable) + trace.recordedArguments.map {
                it.variable
            }
        }
        if (tracerVariables.isNotEmpty()) {
            inlinedOxsts.variables += tracerVariables
            for (branch in inlinedOxsts.mainTransition.branches) {
                for (variable in tracerVariables.reversed()) {
                    branch.steps.addFirst(createReset(variable))
                }
            }
        }
    }

    // a tracer whose assignment the optimizer removed belongs to a call that cannot execute
    fun finalize(inlinedOxsts: InlinedOxsts): TransitionCallTraceMap {
        val (calledTraces, uncalledTraces) = tracerAssignments.entries.partition {
            EcoreUtil.isAncestor(inlinedOxsts, it.key)
        }
        val uncalledVariables = uncalledTraces.flatMap { (assignment, trace) ->
            listOf(assignment.assignedVariable) + trace.recordedArguments.map {
                it.variable
            }
        }.toSet()
        if (uncalledVariables.isNotEmpty()) {
            val resets = inlinedOxsts.eAllOfType<AssignmentOperation>().filter {
                (it.reference as? ElementReference)?.element in uncalledVariables
            }.toList()
            for (reset in resets) {
                EcoreUtil2.remove(reset)
            }
            inlinedOxsts.variables -= uncalledVariables
        }
        return TransitionCallTraceMap(
            calledTraces.associate {
                it.key.assignedVariable to it.value
            },
        )
    }

    private fun createTracerVariable(tracerIndex: Int, context: EObject): VariableDeclaration {
        return createObservationVariable(
            name = TransitionTracerNames.tracerName(tracerIndex),
            typeSpecification = OxstsFactory.createTypeSpecification().also {
                it.domain = builtinSymbolResolver.boolDatatype(context)
            },
            initialValue = OxstsFactory.createLiteralBoolean(false),
            context = context,
        )
    }

    private fun createArgumentVariable(
        tracerIndex: Int,
        parameter: ParameterDeclaration,
        temporary: LocalVarDeclarationOperation,
    ): VariableDeclaration {
        val declaredDomain = parameter.typeSpecification.domain
        val domain = if (declaredDomain is FeatureDeclaration) {
            declaredDomain.typeSpecification.domain
        } else {
            declaredDomain
        }
        val typeSpecification = OxstsFactory.createTypeSpecification().also {
            it.domain = domain
        }
        val initialValue = when {
            domain === builtinSymbolResolver.intDatatype(parameter) -> OxstsFactory.createLiteralInteger(0)
            domain === builtinSymbolResolver.boolDatatype(parameter) -> OxstsFactory.createLiteralBoolean(false)
            domain is EnumDeclaration -> OxstsFactory.createElementReference(domain.literals.first())
            domain is ClassDeclaration -> {
                typeSpecification.multiplicity = OxstsFactory.createDefiniteMultiplicity().also {
                    it.expression = OxstsFactory.createRangeExpression().also { range ->
                        range.left = OxstsFactory.createLiteralInteger(0)
                        range.right = OxstsFactory.createLiteralInteger(1)
                    }
                }
                OxstsFactory.createLiteralNothing()
            }
            else -> sourceError(temporary, "A run-time argument of type '${domain.name}' cannot be recorded for the traced call.")
        }
        return createObservationVariable(
            name = TransitionTracerNames.argumentName(tracerIndex, parameter.name),
            typeSpecification = typeSpecification,
            initialValue = initialValue,
            context = parameter,
        )
    }

    private fun createObservationVariable(
        name: String,
        typeSpecification: TypeSpecification,
        initialValue: Expression,
        context: EObject,
    ): VariableDeclaration {
        return OxstsFactory.createVariableDeclaration().also {
            it.name = name
            it.typeSpecification = typeSpecification
            it.expression = initialValue
            it.annotation = OxstsFactory.createAnnotationContainer().also {
                it.annotations += OxstsFactory.createAnnotation().also {
                    it.declaration = builtinSymbolResolver.nonOptimizableAnnotation(context)
                }
            }
        }
    }

    private fun createReset(variable: VariableDeclaration): AssignmentOperation {
        return OxstsFactory.createAssignmentOperation().also {
            it.reference = OxstsFactory.createElementReference(variable)
            it.expression = variable.expression.copy()
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

    private val AssignmentOperation.assignedVariable: VariableDeclaration
        get() = (reference as ElementReference).element as VariableDeclaration

}
