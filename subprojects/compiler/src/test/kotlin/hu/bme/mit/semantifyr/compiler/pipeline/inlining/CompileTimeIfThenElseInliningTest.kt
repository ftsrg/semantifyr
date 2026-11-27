/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CompileTimeIfThenElseInliningTest : InliningTestBase() {

    private fun store(capacity: Int): String {
        return """
            package inlining::tests::compile_time_if_then_else
            class Item
            class Store {
                refers capacity: int
                var size: int := 0
                var items: Item[0..capacity] := [nothing, ...]
                prop hasHead(item: Item): bool {
                    return if capacity > 0 then items[0] == item else false
                }
                prop maybeHead(item: Item): bool {
                    return if size > 0 then items[0] == item else false
                }
            }
            @VerificationCase
            class Case {
                contains item: Item
                contains store: Store {
                    redefine refers capacity: int = $capacity
                }
                redefine prop {
                    return AG !store.hasHead(item)
                }
            }
            @VerificationCase
            class RuntimeCase {
                contains item: Item
                contains store: Store {
                    redefine refers capacity: int = $capacity
                }
                redefine prop {
                    return AG !store.maybeHead(item)
                }
            }
        """
    }

    @Test
    fun `an if-then-else with a compile-time guard is decided at inlining and the branch not taken is dropped`() {
        prepare("Case", store(0), OptimizationConfig.NONE) {
            val property = inlineAll(it).inlinedOxsts.property
            assertThat(property.eAllOfType<IfThenElse>().toList()).isEmpty()
            assertThat(property.eAllOfType<IndexingSuffixExpression>().toList()).isEmpty()
            val literals = property.eAllOfType<LiteralBoolean>().map { literal ->
                literal.isValue
            }
            assertThat(literals.toList()).containsExactly(false)
        }
        prepare("Case", store(1), OptimizationConfig.NONE) {
            val property = inlineAll(it).inlinedOxsts.property
            assertThat(property.eAllOfType<IfThenElse>().toList()).isEmpty()
            assertThat(property.eAllOfType<IndexingSuffixExpression>().toList()).hasSize(1)
        }
    }

    @Test
    fun `an if-then-else with a run-time guard stays`() {
        prepare("RuntimeCase", store(1), OptimizationConfig.NONE) {
            val property = inlineAll(it).inlinedOxsts.property
            assertThat(property.eAllOfType<IfThenElse>().toList()).hasSize(1)
        }
    }

    private fun compiles(className: String, source: String): List<IfThenElse> {
        return prepare(className, source, OptimizationConfig.NONE) {
            inlineAll(it).inlinedOxsts.eAllOfType<IfThenElse>().toList()
        }
    }

    @Test
    fun `a guard over a parameter is decided where its argument is a constant and stays where it is read at run time`() {
        val source = testModel(javaClass, "parameter_guard.semantifyr")
        assertThat(compiles("Case", source)).hasSize(2)
    }

    @Test
    fun `a guard of compile-time modality the compiler cannot evaluate leaves the expression as it was`() {
        val source = """
            package inlining::tests::compile_time_if_then_else_many_valued
            class Worker
            @VerificationCase
            class Case {
                contains workers: Worker[0..*]
                contains first: Worker subsets workers
                contains second: Worker subsets workers
                var result: int := 0
                redefine tran {
                    result := if workers[0] == first then 1 else 2
                }
                redefine prop {
                    return AG result >= 0
                }
            }
        """
        assertThat(compiles("Case", source)).hasSize(1)
    }

    @Test
    fun `a compile-time guard is decided in an assignment and in the guard of an if of a transition`() {
        val source = testModel(javaClass, "guard_in_assignment_and_if.semantifyr")
        prepare("Case", source, OptimizationConfig.NONE) {
            val main = inlineAll(it).inlinedOxsts.mainTransition
            assertThat(main.eAllOfType<IfThenElse>().toList()).isEmpty()
            assertThat(main.eAllOfType<IndexingSuffixExpression>().toList()).isEmpty()
        }
    }
}
