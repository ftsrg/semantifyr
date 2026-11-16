/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.FlatteningTestBase
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TracedRuntimeArgumentTest : FlatteningTestBase() {

    @Test
    fun `a run-time argument of a traced call is recorded in a variable the trace names`() {
        val compilation = flatten("IntegerArgument", MODEL)

        val trace = compilation.transitionCallTraces.getTransitionCallTrace(compilation.tracer())

        val recorded = trace.arguments.single() as ArgumentTrace.Recorded
        assertThat(recorded.parameterDeclaration.name).isEqualTo("value")
        assertThat(recorded.variable.name).isEqualTo(TransitionTracerNames.argumentName(0, "value"))
        assertThat(compilation.inlinedOxsts.variables).contains(recorded.variable)
        val records = compilation.writesOf(recorded.variable).filter {
            it.expression is ElementReference
        }
        assertThat(records).singleElement().satisfies({
            assertThat((it.expression as ElementReference).element.name).isNotEqualTo(recorded.variable.name)
        })
    }

    @Test
    fun `every step resets the tracer and the recorded argument before it runs`() {
        val compilation = flatten("IntegerArgument", MODEL)
        val recorded = compilation.transitionCallTraces.getTransitionCallTrace(compilation.tracer()).recordedArguments.single().variable

        val resets = compilation.inlinedOxsts.mainTransition.branches.single().steps.take(2).map {
            it as AssignmentOperation
        }

        assertThat(
            resets.map {
                (it.reference as ElementReference).element
            },
        ).containsExactly(compilation.tracer(), recorded)
        assertThat((resets[0].expression as LiteralBoolean).isValue).isFalse()
        assertThat((resets[1].expression as LiteralInteger).value).isEqualTo(0)
    }

    @Test
    fun `a recorded instance argument is an instance id over the instances of its class`() {
        val compilation = flatten("InstanceArgument", MODEL)
        val recorded = compilation.transitionCallTraces.getTransitionCallTrace(compilation.tracer()).recordedArguments.single().variable

        val domain = compilation.flatteningInfo.variableInstanceDomains.getValue(recorded).map {
            it.name.substringAfterLast("$")
        }

        assertThat(domain).containsExactlyInAnyOrder("first", "second")
        assertThat(recorded.typeSpecification.domain.name).isEqualTo("int")
    }

    private fun FlattenedCompilationContext.tracer(): VariableDeclaration {
        return inlinedOxsts.variables.single {
            TransitionTracerNames.isTracerName(it.name)
        }
    }

    private fun FlattenedCompilationContext.writesOf(variable: VariableDeclaration): List<AssignmentOperation> {
        return inlinedOxsts.eAllOfType<AssignmentOperation>().filter {
            (it.reference as? ElementReference)?.element === variable
        }.toList()
    }

    companion object {
        private val MODEL = """
            package inlining::tests::traced_runtime_argument

            class Port {
                var item: int := 0

                @Trace
                tran send(value: int) {
                    item := value
                }
            }

            class Lamp {
                var on: bool := false

                @Trace
                tran toggle(partner: Lamp) {
                    on := !on
                }
            }

            @VerificationCase
            class IntegerArgument {
                contains port: Port
                var count: int := 0

                redefine tran {
                    choice {
                        count := 1
                    } or {
                        count := 2
                    }
                    inline port.send(count)
                }

                prop {
                    return AG port.item >= 0
                }
            }

            @VerificationCase
            class InstanceArgument {
                contains first: Lamp
                contains second: Lamp
                var current: Lamp := first

                redefine tran {
                    choice {
                        current := first
                    } or {
                        current := second
                    }
                    inline first.toggle(current)
                }

                prop {
                    return AG true
                }
            }
        """.trimIndent()
    }

}
