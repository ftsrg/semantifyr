/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.FlatteningTestBase
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObservedCallConeTest : FlatteningTestBase() {

    @Test
    fun `the guard of an observed call is not folded to its initial value`() {
        val compilation = flatten("GuardedCall", MODEL, OptimizationConfig.ALL)

        val armed = compilation.variableEndingWith("armed")
        val tracerSet = compilation.writesOf(compilation.variableStartingWith(TransitionTracerNames.TRACER_PREFIX)).filter {
            (it.expression as? LiteralBoolean)?.isValue == true
        }

        assertThat(compilation.writesOf(armed)).isNotEmpty()
        assertThat(tracerSet).isNotEmpty()
    }

    @Test
    fun `a value an observed argument reads is not folded to its initial value`() {
        val compilation = flatten("RunTimeArgument", MODEL, OptimizationConfig.ALL)

        val records = compilation.writesOf(compilation.variableStartingWith(TransitionTracerNames.ARGUMENT_PREFIX)).filterNot {
            (it.expression as? LiteralInteger)?.value == 0
        }

        assertThat(records).isNotEmpty().allSatisfy({
            assertThat(it.expression).isNotInstanceOf(LiteralInteger::class.java)
        })
    }

    @Test
    fun `a constant an observed argument reads still folds`() {
        val compilation = flatten("ConstantArgument", MODEL, OptimizationConfig.ALL)

        val records = compilation.writesOf(compilation.variableStartingWith(TransitionTracerNames.ARGUMENT_PREFIX)).map {
            (it.expression as LiteralInteger).value
        }

        assertThat(records).containsExactlyInAnyOrder(0, 4)
    }

    private fun FlattenedCompilationContext.variableEndingWith(suffix: String): VariableDeclaration {
        return inlinedOxsts.variables.single {
            it.name.endsWith(suffix)
        }
    }

    private fun FlattenedCompilationContext.variableStartingWith(prefix: String): VariableDeclaration {
        return inlinedOxsts.variables.single {
            it.name.startsWith(prefix)
        }
    }

    private fun FlattenedCompilationContext.writesOf(variable: VariableDeclaration): List<AssignmentOperation> {
        return inlinedOxsts.eAllOfType<AssignmentOperation>().filter {
            (it.reference as? ElementReference)?.element === variable
        }.toList()
    }

    companion object {
        private val MODEL = testModel(ObservedCallConeTest::class.java, "model.semantifyr")
    }

}
