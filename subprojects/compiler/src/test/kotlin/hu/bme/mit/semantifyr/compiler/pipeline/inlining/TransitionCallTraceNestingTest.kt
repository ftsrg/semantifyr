/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.expression.ExpressionEvaluationSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransitionCallTraceNestingTest : InliningTestBase() {

    @Test
    fun `each send is nested under the firing whose body encloses it`() {
        val parents = traceParents(
            """
                redefine tran {
                    inline controller.fire()
                    inline lightA.fire()
                    inline lightB.fire()
                }
            """,
        )

        assertThat(parents).containsExactlyInAnyOrder(
            "root.controller.fire" to null,
            "root.controller.controlA.send" to "root.controller.fire",
            "root.controller.controlB.send" to "root.controller.fire",
            "root.lightA.fire" to null,
            "root.lightA.lightControl.send" to "root.lightA.fire",
            "root.lightB.fire" to null,
            "root.lightB.lightControl.send" to "root.lightB.fire",
        )
    }

    @Test
    fun `a traced call outside any firing stays top-level`() {
        val parents = traceParents(
            """
                redefine init {
                    inline lightA.lightControl.send(1)
                    inline lightB.lightControl.send(1)
                }

                redefine tran {
                    inline lightA.fire()
                    inline passTime()
                }
            """,
        )

        assertThat(parents).containsExactlyInAnyOrder(
            "root.lightA.lightControl.send" to null,
            "root.lightB.lightControl.send" to null,
            "root.lightA.fire" to null,
            "root.lightA.lightControl.send" to "root.lightA.fire",
            "root.passTime" to null,
        )
    }

    @Test
    fun `a send dispatched over a variable is nested under the firing for every candidate`() {
        val parents = traceParents(
            """
                redefine tran {
                    inline controller.fireSelected()
                    inline passTime()
                }
            """,
        )

        assertThat(parents).containsExactlyInAnyOrder(
            "root.controller.fireSelected" to null,
            "root.controller.controlA.send" to "root.controller.fireSelected",
            "root.controller.controlB.send" to "root.controller.fireSelected",
            "root.passTime" to null,
        )
    }

    private fun traceParents(caseBody: String): List<Pair<String, String?>> {
        return prepare("Crossing", model(caseBody)) {
            val inlined = inlineAll(it)
            val serializer = it.compilationInjector.getInstance(ExpressionEvaluationSerializer::class.java)
            fun nameOf(trace: TransitionCallTrace): String {
                return "${serializer.serialize(trace.self)}.${trace.transitionDeclaration.name}"
            }

            inlined.inlinedOxsts.variables.filter {
                TransitionTracerNames.isTracerName(it.name)
            }.map {
                val trace = inlined.transitionCallTraces.getTransitionCallTrace(it)
                nameOf(trace) to trace.parent?.let { parent -> nameOf(parent) }
            }
        }
    }

    private fun model(caseBody: String): String {
        return """
            package inlining::tests::trace_nesting

            class Port {
                var item: int := 0

                @Trace
                tran send(value: int) {
                    item := value
                }
            }

            class Controller {
                contains controls: Port[0..*]
                contains controlA: Port[1] subsets controls
                contains controlB: Port[1] subsets controls
                var selected: controls[0..1] := nothing

                @Trace
                tran fire() {
                    inline sendAll()
                }

                tran sendAll() {
                    inline for (control in controls) {
                        inline control.send(2)
                    }
                }

                @Trace
                tran fireSelected() {
                    inline selected.send(4)
                }
            }

            class Light {
                contains lightControl: Port
                var on: bool := false

                @Trace
                tran fire() {
                    on := !on
                    inline lightControl.send(3)
                }
            }

            @VerificationCase
            class Crossing {
                contains controller: Controller
                contains lightA: Light
                contains lightB: Light
                var time: int := 0

                $caseBody

                @Trace
                tran passTime() {
                    time := time + 1
                }

                prop {
                    return AG (lightA.lightControl.item + lightB.lightControl.item + time >= 0)
                }
            }
        """.trimIndent()
    }

}
