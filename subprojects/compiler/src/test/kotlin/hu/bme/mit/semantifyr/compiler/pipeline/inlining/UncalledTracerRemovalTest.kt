/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.expression.ExpressionEvaluationSerializer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UncalledTracerRemovalTest : InliningTestBase() {

    @Test
    fun `a traced call whose body cannot execute gets no tracer`() {
        prepare("Machine", MODEL) {
            val inlined = inlineAll(it)
            val serializer = it.compilationInjector.getInstance(ExpressionEvaluationSerializer::class.java)
            val tracers = inlined.inlinedOxsts.variables.filter { variable ->
                TransitionTracerNames.isTracerName(variable.name)
            }
            val traced = tracers.map { variable ->
                val trace = inlined.transitionCallTraces.getTransitionCallTrace(variable)
                "${serializer.serialize(trace.self)}.${trace.transitionDeclaration.name}"
            }
            val referencedTracers = inlined.inlinedOxsts.eAllOfType<ElementReference>().mapNotNull { reference ->
                reference.element as? VariableDeclaration
            }.filter { variable ->
                TransitionTracerNames.isTracerName(variable.name)
            }.toSet()

            assertThat(traced).containsExactlyInAnyOrder(
                "root.step.fire",
                "root.step.port.send",
            )
            assertThat(referencedTracers).containsExactlyInAnyOrderElementsOf(tracers)
        }
    }

    companion object {
        private val MODEL = """
            package inlining::tests::uncalled_tracer_removal

            class Port {
                var item: int := 0

                @Trace
                tran send(value: int) {
                    item := value
                }
            }

            abstract class Node {
                abstract prop isActive(): bool
            }

            class State : Node {
                var active: bool := false

                redefine prop isActive(): bool {
                    return active
                }
            }

            class Entry : Node {
                redefine prop isActive(): bool {
                    return false
                }
            }

            class Transition {
                refers from: Node[1]
                contains port: Port

                @Trace
                tran fire() {
                    assume (from.isActive())
                    inline port.send(1)
                }
            }

            @VerificationCase
            class Machine {
                contains start: Entry
                contains idle: State
                contains initial: Transition {
                    redefine refers from: Node[1] = start
                }
                contains step: Transition {
                    redefine refers from: Node[1] = idle
                }

                redefine tran {
                    choice {
                        inline initial.fire()
                    } or {
                        inline step.fire()
                    }
                }

                prop {
                    return AG (initial.port.item + step.port.item >= 0)
                }
            }
        """.trimIndent()
    }

}
