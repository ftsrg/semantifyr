/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.xtext.EcoreUtil2
import org.junit.jupiter.api.Test

/**
 * Single-step tests for [InlineOperationExpander]. Each test exercises one
 * inline-operation kind, calls the expander on the first matching node in
 * the input's main transition, substitutes the result in place, and compares
 * the whole inlined-oxsts against an expected serialization.
 */
class InlineOperationExpanderTest : ExpanderTestBase() {

    @Test
    fun `inline if with a true static guard returns the then branch`() = assertFirstExpansion(
        input = """
            inlined oxsts of semantifyr::Anything
            var taken : int := 0
            init { }
            tran {
                inline if (1 < 2) {
                    taken := 1
                } else {
                    taken := 2
                }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var taken : int := 0
            init { }
            tran {
                {
                    taken := 1
                }
            }
            prop { AG true }
        """,
    )

    @Test
    fun `inline if with a false static guard returns the else branch`() = assertFirstExpansion(
        input = """
            inlined oxsts of semantifyr::Anything
            var taken : int := 0
            init { }
            tran {
                inline if (1 > 2) {
                    taken := 1
                } else {
                    taken := 2
                }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var taken : int := 0
            init { }
            tran {
                {
                    taken := 2
                }
            }
            prop { AG true }
        """,
    )

    @Test
    fun `inline if with a false guard and no else branch returns empty sequence`() = assertFirstExpansion(
        input = """
            inlined oxsts of semantifyr::Anything
            var taken : int := 0
            init { }
            tran {
                inline if (1 > 2) {
                    taken := 1
                }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var taken : int := 0
            init { }
            tran {
                { }
            }
            prop { AG true }
        """,
    )

    @Test
    fun `inline seq for over an integer range unrolls into sequential steps`() = assertFirstExpansion(
        input = """
            inlined oxsts of semantifyr::Anything
            var total : int := 0
            init { }
            tran {
                inline for (n in 1..3) {
                    total := total + n
                }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var total : int := 0
            init { }
            tran {
                {
                    {
                        total := total + 1
                    }
                    {
                        total := total + 2
                    }
                    {
                        total := total + 3
                    }
                }
            }
            prop { AG true }
        """,
    )

    @Test
    fun `inline choice for over an integer range produces a choice over each value`() = assertFirstExpansion(
        input = """
            inlined oxsts of semantifyr::Anything
            var chosen : int := 0
            init { }
            tran {
                inline for choice (n in 1..3) {
                    chosen := n
                }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var chosen : int := 0
            init { }
            tran {
                choice {
                    chosen := 1
                } or {
                    chosen := 2
                } or {
                    chosen := 3
                }
            }
            prop { AG true }
        """,
    )

    @Test
    fun `inline seq for over an empty range with no else branch produces empty sequence`() = assertFirstExpansion(
        input = """
            inlined oxsts of semantifyr::Anything
            var total : int := 0
            init { }
            tran {
                inline for (n in 5..3) {
                    total := total + n
                }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var total : int := 0
            init { }
            tran {
                { }
            }
            prop { AG true }
        """,
    )

    @org.junit.jupiter.api.Disabled(
        "Needs an OxstsPackage-based ExpanderTestBase. The dispatching logic " +
            "is implemented (CallTarget.VariableDispatch branch in " +
            "InlineOperationExpander); covered end-to-end via " +
            "gamma/sysmlv2 verification suites when a model uses " +
            "inline-through-feature-var."
    )
    @Test
    fun `inline call through a feature-typed variable dispatches over every candidate instance`() {
        // Pseudocode of intended fixture:
        //   class Leaf { var v: int := 0; tran step() { v := v + 1 } }
        //   @VerificationCase class Dispatcher {
        //       contains leaves: Leaf[0..*]
        //       var chosen: leaves[1]
        //       redefine tran { inline chosen.step() }
        //       prop { AG true }
        //   }
        // Expected expansion of `inline chosen.step()`:
        //   choice {
        //       assume(chosen == leaves1); { leaves1.v := leaves1.v + 1 }
        //   } or {
        //       assume(chosen == leaves2); { leaves2.v := leaves2.v + 1 }
        //   } or ...
    }

    @Test
    fun `inline seq for over a negative integer range unrolls in ascending order`() = assertFirstExpansion(
        input = """
            inlined oxsts of semantifyr::Anything
            var total : int := 0
            init { }
            tran {
                inline for (n in -2..0) {
                    total := total + n
                }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var total : int := 0
            init { }
            tran {
                {
                    {
                        total := total + -2
                    }
                    {
                        total := total + -1
                    }
                    {
                        total := total + 0
                    }
                }
            }
            prop { AG true }
        """,
    )

    @Test
    fun `inline for over an unbounded range is rejected with a sourceError`() {
        val fixture = prepare(
            """
                inlined oxsts of semantifyr::Anything
                var total : int := 0
                init { }
                tran {
                    inline for (n in 0..*) {
                        total := total + n
                    }
                }
                prop { AG true }
            """,
        )
        val expander = fixture.compilationInjector.getInstance(InlineOperationExpander::class.java)
        val firstInline = firstInlineIn(fixture)

        assertThatThrownBy { expander.expand(firstInline, fixture.rootInstance) { 0 } }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("bounded range")
    }

    private fun assertFirstExpansion(input: String, expected: String) {
        val fixture = prepare(input)
        val firstInline = firstInlineIn(fixture)

        val expander = fixture.compilationInjector.getInstance(InlineOperationExpander::class.java)
        var counter = 0
        val expanded = expander.expand(firstInline, fixture.rootInstance) { counter++ }
        EcoreUtil2.replace(firstInline, expanded)

        assertSerializedModelEquals(fixture.inlinedOxsts, expected)
    }

    private fun firstInlineIn(fixture: Prepared): InlineOperation {
        val main = fixture.inlinedOxsts.mainTransition
            ?: error("Input fixture must have a main transition")
        return main.eAllOfType<InlineOperation>().firstOrNull()
            ?: error("Input fixture must contain at least one InlineOperation in its main transition")
    }
}
