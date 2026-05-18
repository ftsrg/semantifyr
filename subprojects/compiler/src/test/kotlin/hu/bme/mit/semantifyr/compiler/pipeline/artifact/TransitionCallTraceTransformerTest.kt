/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionCallTraceTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TransitionCallTraceTransformerTest {
    private val evaluatorProvider: CompileTimeExpressionEvaluatorProvider = mock()
    private val evaluator: CompileTimeExpressionEvaluator = mock()
    private val builtinSymbolResolver: BuiltinSymbolResolver = mock()
    private val instance: Instance = mock()
    private val container: Instance = mock()

    private val boolDatatype = OxstsFactory.createDataTypeDeclaration().also {
        it.name = "bool"
    }
    private val controlAnnotation = OxstsFactory.createAnnotationDeclaration().also {
        it.name = "Control"
    }
    private val nonOptimizableAnnotation = OxstsFactory.createAnnotationDeclaration().also {
        it.name = "NonOptimizable"
    }

    private val emptyCallExpression = OxstsFactory.createCallSuffixExpression()

    private fun mockTransitionDeclaration(): TransitionDeclaration {
        return mock()
    }

    private fun newTransformer(): TransitionCallTraceTransformer {
        whenever(evaluatorProvider.getEvaluator(any())).thenReturn(evaluator)
        whenever(builtinSymbolResolver.boolDatatype(any())).thenReturn(boolDatatype)
        whenever(builtinSymbolResolver.controlAnnotation(any())).thenReturn(controlAnnotation)
        whenever(builtinSymbolResolver.nonOptimizableAnnotation(any())).thenReturn(nonOptimizableAnnotation)
        return TransitionCallTraceTransformer(evaluatorProvider, builtinSymbolResolver)
    }

    @Test
    fun `traceTransitionCall returns an assignment of true to a fresh tracer variable`() {
        val transformer = newTransformer()

        val operation = transformer.traceTransitionCall(
            instance,
            container,
            mockTransitionDeclaration(),
            emptyCallExpression,
        )

        assertThat(operation).isInstanceOf(AssignmentOperation::class.java)
        val reference = operation.reference as ElementReference
        val variable = reference.element as VariableDeclaration
        assertThat(TransitionTracerNames.isTracerName(variable.name)).isTrue
        val literal = operation.expression as LiteralBoolean
        assertThat(literal.isValue).isTrue
    }

    @Test
    fun `tracer variable is annotated NonOptimizable and Control with bool default false`() {
        val transformer = newTransformer()

        val operation = transformer.traceTransitionCall(
            instance,
            container,
            mockTransitionDeclaration(),
            emptyCallExpression,
        )

        val variable = (operation.reference as ElementReference).element as VariableDeclaration
        assertThat(variable.typeSpecification.domain).isSameAs(boolDatatype)
        val initialLiteral = variable.expression as LiteralBoolean
        assertThat(initialLiteral.isValue).isFalse
        val annotationDeclarations = variable.annotation.annotations.map {
            it.declaration
        }
        assertThat(annotationDeclarations).containsExactlyInAnyOrder(controlAnnotation, nonOptimizableAnnotation)
    }

    @Test
    fun `consecutive trace calls allocate distinct tracer variables`() {
        val transformer = newTransformer()

        val firstName = tracerVariableNameOf(transformer.traceTransitionCall(instance, container, mockTransitionDeclaration(), emptyCallExpression))
        val secondName = tracerVariableNameOf(transformer.traceTransitionCall(instance, container, mockTransitionDeclaration(), emptyCallExpression))
        val thirdName = tracerVariableNameOf(transformer.traceTransitionCall(instance, container, mockTransitionDeclaration(), emptyCallExpression))

        assertThat(listOf(firstName, secondName, thirdName)).doesNotHaveDuplicates()
        listOf(firstName, secondName, thirdName).forEach {
            assertThat(TransitionTracerNames.isTracerName(it)).isTrue
        }
    }

    @Test
    fun `finalize on an empty transformer returns an empty map`() {
        val inlinedOxsts = OxstsFactory.createInlinedOxsts().also {
            it.mainTransition = OxstsFactory.createTransitionDeclaration()
        }

        val map = newTransformer().finalize(inlinedOxsts)

        assertThat(inlinedOxsts.variables).isEmpty()
        assertThat(inlinedOxsts.mainTransition.branches).isEmpty()
        val anyVariable: VariableDeclaration = mock()
        assertThat(
            runCatching {
                map.getTransitionCallTrace(anyVariable)
            }.isFailure,
        ).isTrue
    }

    @Test
    fun `finalize registers tracer variables and prepends resets to every main transition branch`() {
        val transformer = newTransformer()
        val inlinedOxsts = OxstsFactory.createInlinedOxsts().also {
            it.mainTransition = OxstsFactory.createTransitionDeclaration().also { transition ->
                transition.branches += OxstsFactory.createSequenceOperation()
                transition.branches += OxstsFactory.createSequenceOperation()
            }
        }
        val firstAssignment = transformer.traceTransitionCall(instance, container, mockTransitionDeclaration(), emptyCallExpression)
        val secondAssignment = transformer.traceTransitionCall(instance, container, mockTransitionDeclaration(), emptyCallExpression)
        val firstVariable = (firstAssignment.reference as ElementReference).element as VariableDeclaration
        val secondVariable = (secondAssignment.reference as ElementReference).element as VariableDeclaration

        transformer.finalize(inlinedOxsts)

        assertThat(inlinedOxsts.variables).containsExactly(firstVariable, secondVariable)
        for (branch in inlinedOxsts.mainTransition.branches) {
            val resetTargets = branch.steps.take(2).map {
                ((it as AssignmentOperation).reference as ElementReference).element as VariableDeclaration
            }
            assertThat(resetTargets).containsExactly(firstVariable, secondVariable)
            for (step in branch.steps.take(2)) {
                val literal = (step as AssignmentOperation).expression as LiteralBoolean
                assertThat(literal.isValue).isFalse
            }
        }
    }

    @Test
    fun `finalize returns a map that resolves recorded traces by tracer variable`() {
        val transformer = newTransformer()
        val transition = mockTransitionDeclaration()
        val inlinedOxsts = OxstsFactory.createInlinedOxsts().also {
            it.mainTransition = OxstsFactory.createTransitionDeclaration()
        }

        val operation = transformer.traceTransitionCall(instance, container, transition, emptyCallExpression)
        val variable = (operation.reference as ElementReference).element as VariableDeclaration
        val map = transformer.finalize(inlinedOxsts)

        val trace = map.getTransitionCallTrace(variable)
        assertThat(trace.transitionDeclaration).isSameAs(transition)
        assertThat(trace.self.instances).containsExactly(container)
        assertThat(trace.parent).isNull()
    }

    private fun tracerVariableNameOf(operation: AssignmentOperation): String {
        val reference = operation.reference as ElementReference
        val variable = reference.element as VariableDeclaration
        return variable.name
    }
}
