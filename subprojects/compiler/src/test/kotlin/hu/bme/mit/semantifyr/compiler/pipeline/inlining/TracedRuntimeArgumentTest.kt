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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
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
            val temporary = (it.expression as ElementReference).element as LocalVarDeclarationOperation
            assertThat(((temporary.expression as ElementReference).element as VariableDeclaration).name).endsWith("count")
        })
    }

    @Test
    fun `a recorded boolean argument starts false`() {
        val recorded = flatten("BooleanArgument", MODEL).recordedVariable()

        assertThat(recorded.typeSpecification.domain.name).isEqualTo("bool")
        assertThat((recorded.expression as LiteralBoolean).isValue).isFalse()
    }

    @Test
    fun `a recorded enum argument starts at the enum's first literal`() {
        val recorded = flatten("EnumArgument", MODEL).recordedVariable()

        assertThat(recorded.typeSpecification.domain.name).isEqualTo("Mode")
        assertThat((recorded.expression as ElementReference).element.name).isEqualTo("Off")
    }

    @Test
    fun `a recorded real argument starts at zero`() {
        val recorded = flatten("RealArgument", MODEL).recordedVariable()

        assertThat(recorded.typeSpecification.domain.name).isEqualTo("real")
        assertThat((recorded.expression as LiteralReal).value).isEqualTo(0.0)
    }

    @Test
    fun `a recorded string argument starts empty`() {
        val recorded = flatten("StringArgument", MODEL).recordedVariable()

        assertThat(recorded.typeSpecification.domain.name).isEqualTo("string")
        assertThat((recorded.expression as LiteralString).value).isEmpty()
    }

    @Test
    fun `an argument of an any parameter is recorded in the argument's own type`() {
        val recorded = flatten("AnyArgument", MODEL).recordedVariable()

        assertThat(recorded.typeSpecification.domain.name).isEqualTo("bool")
        assertThat((recorded.expression as LiteralBoolean).isValue).isFalse()
    }

    @Test
    fun `an argument forwarded through an any parameter is recorded in the type it started with`() {
        val recorded = flatten("ForwardedAnyArgument", MODEL).recordedVariable()

        assertThat(recorded.typeSpecification.domain.name).isEqualTo("bool")
        assertThat((recorded.expression as LiteralBoolean).isValue).isFalse()
    }

    @Test
    fun `an any variable passed to an any parameter is recorded as any starting at zero`() {
        val recorded = flatten("AnyVariableArgument", MODEL).recordedVariable()

        assertThat(recorded.typeSpecification.domain.name).isEqualTo("any")
        assertThat((recorded.expression as LiteralInteger).value).isEqualTo(0)
    }

    @Test
    fun `an any local passed to an any parameter is recorded as any, its writes not followed`() {
        val recorded = flatten("AnyLocalArgument", MODEL).recordedVariable()

        assertThat(recorded.typeSpecification.domain.name).isEqualTo("any")
        assertThat((recorded.expression as LiteralInteger).value).isEqualTo(0)
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

    private fun FlattenedCompilationContext.recordedVariable(): VariableDeclaration {
        return transitionCallTraces.getTransitionCallTrace(tracer()).recordedArguments.single().variable
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

            enum Mode {
                Off,
                On,
            }

            class Switch {
                var mode: Mode := Mode::Off
                var enabled: bool := false

                @Trace
                tran set(next: Mode) {
                    mode := next
                }

                @Trace
                tran enable(value: bool) {
                    enabled := value
                }
            }

            class Display {
                var reading: real := 0.0
                var label: string := ""
                var seen: bool := false

                @Trace
                tran show(value: real) {
                    reading := value
                }

                @Trace
                tran write(text: string) {
                    label := text
                }

                @Trace
                tran note(value: any) {
                    seen := true
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
            class BooleanArgument {
                contains device: Switch
                var requested: bool := false

                redefine tran {
                    havoc(requested)
                    inline device.enable(requested)
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class EnumArgument {
                contains device: Switch
                var requested: Mode := Mode::Off

                redefine tran {
                    choice {
                        requested := Mode::Off
                    } or {
                        requested := Mode::On
                    }
                    inline device.set(requested)
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class RealArgument {
                contains display: Display
                var level: real := 0.5

                redefine tran {
                    inline display.show(level)
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class StringArgument {
                contains display: Display
                var name: string := "a"

                redefine tran {
                    inline display.write(name)
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class AnyArgument {
                contains display: Display
                var requested: bool := false

                redefine tran {
                    havoc(requested)
                    inline display.note(requested)
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class ForwardedAnyArgument {
                contains display: Display
                var requested: bool := false

                tran relay(value: any) {
                    inline display.note(value)
                }

                redefine tran {
                    havoc(requested)
                    inline relay(requested)
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class AnyVariableArgument {
                contains display: Display
                var stored: any := 0

                redefine tran {
                    havoc(stored)
                    inline display.note(stored)
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class AnyLocalArgument {
                contains display: Display
                var requested: bool := false

                redefine tran {
                    var held: any := 0
                    havoc(requested)
                    held := requested
                    inline display.note(held)
                }

                prop {
                    return AG true
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
