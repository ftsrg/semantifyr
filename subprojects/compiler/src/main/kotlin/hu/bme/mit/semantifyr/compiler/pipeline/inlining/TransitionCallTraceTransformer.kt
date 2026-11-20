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
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.EcoreUtil2

sealed interface ArgumentTrace {
    val parameterDeclaration: ParameterDeclaration

    class Constant(
        override val parameterDeclaration: ParameterDeclaration,
        val evaluation: ExpressionEvaluation,
    ) : ArgumentTrace

    class Recorded(
        override val parameterDeclaration: ParameterDeclaration,
        val variable: VariableDeclaration,
    ) : ArgumentTrace
}

class TransitionCallTrace(
    val self: InstanceEvaluation,
    val transitionDeclaration: TransitionDeclaration,
    val arguments: List<ArgumentTrace>,
    val parent: TransitionCallTrace?,
) {
    val recordedArguments: List<ArgumentTrace.Recorded>
        get() = arguments.filterIsInstance<ArgumentTrace.Recorded>()
}

class TracedCall(
    val tracerAssignment: AssignmentOperation,
    val argumentRecords: List<AssignmentOperation>,
)

@CompilationScoped
class TransitionCallTraceTransformer @Inject constructor(
    private val compileTimeExpressionEvaluatorProvider: CompileTimeExpressionEvaluatorProvider,
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val expressionTypeEvaluatorProvider: ExpressionTypeEvaluatorProvider,
) {

    private var tracerCount = 0
    private val tracerAssignments = mutableMapOf<AssignmentOperation, TransitionCallTrace>()

    // inlined sequences are spliced into their parent, so the enclosing call is recorded per pending inline operation
    private val enclosingTraces = mutableMapOf<InlineOperation, TransitionCallTrace>()

    // a temporary is written once, by its initializer, so its initializer is the value a forwarded argument carries
    private val argumentTemporaries = mutableSetOf<LocalVarDeclarationOperation>()

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

        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)
        val argumentRecords = mutableListOf<AssignmentOperation>()
        val transitionCallTrace = TransitionCallTrace(
            self = InstanceEvaluation(containerInstance),
            transitionDeclaration = transitionDeclaration,
            arguments = transitionDeclaration.parameters.withIndex().mapNotNull { (index, parameter) ->
                val argument = OxstsUtils.getArgumentFor(callExpression.arguments, parameter, index)
                val temporary = runtimeArguments[parameter]
                if (argument != null && temporary != null) {
                    val variable = createArgumentVariable(tracerIndex, transitionDeclaration, parameter, argument.expression)
                    argumentRecords += OxstsFactory.createAssignmentOperation().also {
                        it.reference = OxstsFactory.createElementReference(variable)
                        it.expression = OxstsFactory.createElementReference(temporary)
                    }
                    return@mapNotNull ArgumentTrace.Recorded(parameter, variable)
                }
                argument?.let {
                    val evaluation = try {
                        evaluator.evaluateTyped(ExpressionEvaluation::class.java, it.expression)
                    } catch (_: EvaluationFailureException) {
                        sourceError(it.expression, "The argument of traced transition '${transitionDeclaration.name}' is not evaluable at compile time")
                    }
                    ArgumentTrace.Constant(parameter, evaluation)
                }
            },
            parent = parent,
        )
        enclose(body, transitionCallTrace)

        val tracerAssignment = createTracerAssignment(tracerVariable, value = true)
        tracerAssignments[tracerAssignment] = transitionCallTrace
        return TracedCall(tracerAssignment, argumentRecords)
    }

    fun registerArgumentTemporaries(temporaries: Collection<LocalVarDeclarationOperation>) {
        argumentTemporaries += temporaries
    }

    fun inheritEnclosingTrace(operation: InlineOperation, expansion: Operation) {
        val enclosingTrace = enclosingTraces.remove(operation) ?: return
        enclose(expansion, enclosingTrace)
    }

    fun traceMap(): TransitionCallTraceMap {
        return traceMapOf(tracerAssignments.entries)
    }

    fun declareTracers(inlinedOxsts: InlinedOxsts) {
        val observedVariables = traceMap().observedVariables
        if (observedVariables.isNotEmpty()) {
            inlinedOxsts.variables += observedVariables
            for (branch in inlinedOxsts.mainTransition.branches) {
                for (variable in observedVariables.reversed()) {
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
        val uncalledVariables = traceMapOf(uncalledTraces).observedVariables
        for ((assignment, _) in uncalledTraces) {
            tracerAssignments.remove(assignment)
        }
        if (uncalledVariables.isNotEmpty()) {
            val resets = inlinedOxsts.eAllOfType<AssignmentOperation>().filter {
                (it.reference as? ElementReference)?.element in uncalledVariables
            }.toList()
            for (reset in resets) {
                EcoreUtil2.remove(reset)
            }
            inlinedOxsts.variables -= uncalledVariables
        }
        return traceMapOf(calledTraces)
    }

    private fun traceMapOf(traces: Collection<Map.Entry<AssignmentOperation, TransitionCallTrace>>): TransitionCallTraceMap {
        return TransitionCallTraceMap(
            traces.associate {
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
        transitionDeclaration: TransitionDeclaration,
        parameter: ParameterDeclaration,
        argument: Expression,
    ): VariableDeclaration {
        val domain = recordedDomainOf(parameter, argument)
        val typeSpecification = OxstsFactory.createTypeSpecification().also {
            it.domain = domain
        }
        val initialValue = when {
            domain === builtinSymbolResolver.intDatatype(parameter) -> OxstsFactory.createLiteralInteger(0)
            domain === builtinSymbolResolver.boolDatatype(parameter) -> OxstsFactory.createLiteralBoolean(false)
            domain === builtinSymbolResolver.realDatatype(parameter) -> OxstsFactory.createLiteralReal().also {
                it.value = 0.0
            }
            domain === builtinSymbolResolver.stringDatatype(parameter) -> OxstsFactory.createLiteralString().also {
                it.value = ""
            }
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
            domain === builtinSymbolResolver.anyDatatype(parameter) -> OxstsFactory.createLiteralInteger(0)
            domain is RecordDeclaration -> {
                sourceError(argument, "Record arguments of the traced transition '${transitionDeclaration.name}' are not yet supported by the compiler, the witness cannot record a record value.")
            }
            else -> error("No recording variable for a run-time argument of type '${domain?.name}'")
        }
        return createObservationVariable(
            name = TransitionTracerNames.argumentName(tracerIndex, parameter.name),
            typeSpecification = typeSpecification,
            initialValue = initialValue,
            context = parameter,
        )
    }

    // an `any` parameter records the value the argument has, of the argument's own type, through forwarding calls
    private fun recordedDomainOf(parameter: ParameterDeclaration, argument: Expression): DomainDeclaration? {
        val anyDatatype = builtinSymbolResolver.anyDatatype(parameter)
        var domain = classOrDatatypeOf(parameter.typeSpecification.domain)
        var value: Expression? = argument
        while (domain === anyDatatype && value != null) {
            domain = classOrDatatypeOf(expressionTypeEvaluatorProvider.evaluate(value).domain)
            value = ((value as? ElementReference)?.element as? LocalVarDeclarationOperation)?.takeIf {
                it in argumentTemporaries
            }?.expression
        }
        return domain
    }

    private fun classOrDatatypeOf(domain: DomainDeclaration?): DomainDeclaration? {
        if (domain is FeatureDeclaration) {
            return domain.typeSpecification.domain
        }
        return domain
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
