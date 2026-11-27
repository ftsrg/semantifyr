/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import hu.bme.mit.semantifyr.backend.testModel
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionCallTrace
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpinCapabilityCheckerTest : SpinTransformationTestBase() {

    private val colors = """
        package test::colors
        enum Color {
            Red,
            Green,
        }
    """

    private fun unsupported(body: String): List<String> {
        return unsupportedIn("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}")
    }

    private fun unsupportedIn(model: String): List<String> {
        val inlinedOxsts = parse(model, colors)
        return withCapabilityChecker {
            it.check(inlinedOxsts, TransitionCallTraceMap(emptyMap()))
        }.map {
            it.description
        }
    }

    @Test
    fun `a model of supported constructs passes`() {
        val unsupported = unsupportedIn(testModel("supported_constructs.semantifyr"))

        assertThat(unsupported).isEmpty()
    }

    @Test
    fun `every unsupported construct is reported`() {
        val unsupported = unsupported(
            """
                var ratio : real := 0.5
                var anything : any := 0
                var text : string := "text"
                var counter : int := 0
                init {
                }
                tran {
                    for (index in 0..2) {
                        counter := counter + index
                    }
                }
                prop {
                    AG (if counter < 3 then 1 else 0) == 1
                }
            """,
        )

        assertThat(unsupported).containsExactlyInAnyOrder(
            "variables of type real",
            "variables of type any",
            "real literals",
            "variables of type string",
            "string literals",
            "for-operations",
            "non-boolean if-then-else inside a property",
            "unbounded integer variables",
        )
    }

    @Test
    fun `an unbounded integer is reported only when the property depends on it`() {
        val unsupported = unsupported(
            """
                var counted : int := 0
                var ignored : int := 0
                init {
                }
                tran {
                    counted := counted + 1
                    ignored := ignored + 1
                }
                prop {
                    AG counted >= 0
                }
            """,
        )

        assertThat(unsupported).containsExactly("unbounded integer variables")
    }

    @Test
    fun `an unbounded integer is reported when the trace map records it and not when it only bears a tracer's name`() {
        val tracerName = TransitionTracerNames.tracerName(0)
        val inlinedOxsts = parse(
            """
                inlined oxsts of semantifyr::Anything
                var recorded : int := 0
                var $tracerName : int := 0
                init {
                }
                tran {
                    recorded := recorded + 1
                    $tracerName := $tracerName + 1
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )
        val recorded = inlinedOxsts.variables.single {
            it.name == "recorded"
        }
        val trace = TransitionCallTrace(
            self = InstanceEvaluation(emptySet()),
            transitionDeclaration = OxstsFactory.createTransitionDeclaration(),
            arguments = emptyList(),
            parent = null,
        )

        val unsupported = withCapabilityChecker {
            it.check(inlinedOxsts, TransitionCallTraceMap(mapOf(recorded to trace)))
        }

        assertThat(unsupported).singleElement().satisfies({
            assertThat(it.element).isSameAs(recorded)
            assertThat(it.description).isEqualTo("unbounded integer variables")
        })
    }

    @Test
    fun `an unbounded integer that guards an assumption is reported`() {
        val unsupported = unsupported(
            """
                var counter : int := 0
                var observed : int := 0
                init {
                }
                tran {
                    choice {
                        counter := counter + 1
                    } or {
                        if (counter <= 5) {
                            assume(false)
                        }
                        observed := 1
                    }
                }
                prop {
                    AG observed == 0
                }
            """,
        )

        assertThat(unsupported).containsExactly("unbounded integer variables")
    }

    @Test
    fun `a bounded integer beyond 32 bits is reported`() {
        val unsupported = unsupported(
            """
                var counter : int := 1
                init {
                }
                tran {
                    if (counter < 100000) {
                        counter := counter * 100000
                    }
                }
                prop {
                    AG counter > 0
                }
            """,
        )

        assertThat(unsupported).containsExactly("integer variables beyond the 32-bit range")
    }

    @Test
    fun `arithmetic beyond 32 bits over bounded integers is reported`() {
        val unsupported = unsupported(
            """
                var counter : int := 0
                var scaled : int := 0
                init {
                }
                tran {
                    if (counter < 5000) {
                        counter := counter + 1
                    }
                    scaled := (counter * 1000000) / 1000000
                }
                prop {
                    AG scaled <= 5000
                }
            """,
        )

        assertThat(unsupported).containsExactly("arithmetic that may leave the 32-bit range")
    }

    @Test
    fun `an integer chosen among havoc candidates is bounded by them`() {
        val unsupported = unsupported(
            """
                var counter : int := 0
                init {
                }
                tran {
                    havoc(counter)
                }
                prop {
                    AG counter < 5
                }
            """,
        )

        assertThat(unsupported).isEmpty()
    }

    private fun unsupportedWithClockBound(bound: Int): List<String> {
        return unsupported(
            """
                var elapsed : clock := 0
                var late : bool := false
                init {
                }
                tran {
                    choice {
                        assume(elapsed >= $bound)
                        late := true
                        elapsed := 0
                    } or {
                    }
                }
                prop {
                    AG !late
                }
            """,
        )
    }

    @Test
    fun `the largest clock bound whose whole delay stays within 32 bits is accepted`() {
        assertThat(unsupportedWithClockBound(1073741822)).isEmpty()
    }

    @Test
    fun `a clock bound one above it is reported, since the clock and its delay would reach 2 to the 31`() {
        assertThat(unsupportedWithClockBound(1073741823)).containsExactly("clocks that a whole delay may carry beyond the 32-bit range")
    }
}
