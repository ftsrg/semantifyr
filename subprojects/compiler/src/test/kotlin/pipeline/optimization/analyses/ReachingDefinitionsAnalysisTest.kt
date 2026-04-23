/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.junit.jupiter.api.Test

class ReachingDefinitionsAnalysisTest : AnalysisTestBase() {

    @Test
    fun `property read after a single init write sees exactly that write`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { a := 1 }
                tran { }
                prop { AG (a != 27) }
            """,
        )

        val a = inlined.varNamed("a")
        val propertyRead = inlined.findPropertyReadOf(a)
        val defs = result.defsOf[propertyRead]!!

        val initWrite = inlined.assignmentsTo(a).single()
        assertThat(defs).containsExactly(initWrite as EObject)
    }

    @Test
    fun `property read sees every write to a variable`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { a := 1 }
                tran {
                    a := 2
                    a := 3
                }
                prop { AG (a != 27) }
            """,
        )

        val a = inlined.varNamed("a")
        val propertyRead = inlined.findPropertyReadOf(a)
        val defs = result.defsOf[propertyRead]!!

        val writes = inlined.assignmentsTo(a)
        assertThat(writes).hasSize(3)
        for (write in writes) {
            assertThat(defs).contains(write as EObject)
        }
        assertThat(defs).doesNotContain(a as EObject)
    }

    @Test
    fun `intra-transition read after single assignment sees exactly that assignment`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    a := 5
                    b := a
                }
                prop { AG true }
            """,
        )

        val a = inlined.varNamed("a")
        val aWrite = inlined.assignmentsTo(a).single()

        val bAssignment = inlined.assignmentsTo(inlined.varNamed("b")).single()
        val aReadInBRhs = bAssignment.expression as ElementReference
        assertThat(aReadInBRhs.element).isSameAs(a)

        val defs = result.defsOf[aReadInBRhs]
            ?: error("RHS read of 'a' in 'b := a' not in defsOf map")

        assertThat(defs).containsExactly(aWrite as EObject)
    }

    @Test
    fun `havoc counts as a reaching definition`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { }
                tran {
                    havoc(a)
                }
                prop { AG (a != 27) }
            """,
        )

        val a = inlined.varNamed("a")
        val propertyRead = inlined.findPropertyReadOf(a)
        val defs = result.defsOf[propertyRead]!!

        val havoc = inlined.havocsOn(a).single()
        assertThat(defs).containsExactly(havoc as EObject)
    }

    @Test
    fun `guard read in assume sees reaching defs at that program point`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    a := 5
                    assume(a == 5)
                    b := 1
                }
                prop { AG true }
            """,
        )

        val a = inlined.varNamed("a")
        val aWrite = inlined.assignmentsTo(a).single()

        val assume = inlined.assumesReading(a).single()
        val aReadInGuard = (assume.expression as ComparisonOperator).left as ElementReference

        val defs = result.defsOf[aReadInGuard]
            ?: error("Read of 'a' inside assume guard not in defsOf map")

        assertThat(defs).containsExactly(aWrite as EObject)
    }

    @Test
    fun `state-machine-style consecutive writes all reach the property read`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var activeState : int := -1
                init { activeState := 0 }
                tran {
                    choice {
                        assume(activeState == 0)
                        activeState := -1
                        activeState := 25
                    }
                }
                prop { AG (activeState != 25) }
            """,
        )

        val activeState = inlined.varNamed("activeState")
        val propertyRead = inlined.findPropertyReadOf(activeState)
        val defs = result.defsOf[propertyRead]!!

        val writes = inlined.assignmentsTo(activeState)
        assertThat(writes).hasSize(3)  // init + two inside the branch
        for (write in writes) {
            assertThat(defs)
                .`as`("property read should see write $write")
                .contains(write as EObject)
        }
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull { it.name == name }
            ?: error("No variable named '$name' in inlined oxsts")
    }

    private fun InlinedOxsts.assignmentsTo(variable: VariableDeclaration): List<AssignmentOperation> {
        return eAllOfType<AssignmentOperation>().filter {
            val ref = it.reference
            ref is ElementReference && ref.element === variable
        }.toList()
    }

    private fun InlinedOxsts.havocsOn(variable: VariableDeclaration): List<HavocOperation> {
        return eAllOfType<HavocOperation>().filter {
            val ref = it.reference
            ref is ElementReference && ref.element === variable
        }.toList()
    }

    private fun InlinedOxsts.assumesReading(variable: VariableDeclaration): List<AssumptionOperation> {
        return eAllOfType<AssumptionOperation>().filter { assume ->
            assume.expression.eAllOfType<ElementReference>().any { it.element === variable }
        }.toList()
    }

    private fun InlinedOxsts.findPropertyReadOf(variable: VariableDeclaration): Expression {
        val property = eAllOfType<PropertyDeclaration>().first()
        return property.expression.eAllOfType<ElementReference>().firstOrNull { it.element === variable }
            ?: error("Property does not reference '${variable.name}'")
    }
}
