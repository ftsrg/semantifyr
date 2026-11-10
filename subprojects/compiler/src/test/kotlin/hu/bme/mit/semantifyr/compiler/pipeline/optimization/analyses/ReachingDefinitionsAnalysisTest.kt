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
    fun `property read after a single init write sees only that write`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var value: int := 0
                init {
                    value := 1
                }
                tran {
                }
                prop {
                    AG (value != 27)
                }
            """,
        )

        val value = inlined.varNamed("value")
        val propertyRead = inlined.findPropertyReadOf(value)
        val definitions = result.definitionsOf[propertyRead]!!

        val initWrite = inlined.assignmentsTo(value).single()
        assertThat(definitions).containsExactly(initWrite as EObject)
    }

    @Test
    fun `property read sees every write to a variable but not the initializer init overwrites`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var value: int := 0
                init {
                    value := 1
                }
                tran {
                    value := 2
                    value := 3
                }
                prop {
                    AG (value != 27)
                }
            """,
        )

        val value = inlined.varNamed("value")
        val propertyRead = inlined.findPropertyReadOf(value)
        val definitions = result.definitionsOf[propertyRead]!!

        val writes = inlined.assignmentsTo(value)
        assertThat(writes).hasSize(3)
        for (write in writes) {
            assertThat(definitions).contains(write as EObject)
        }
        assertThat(definitions).doesNotContain(value as EObject)
    }

    @Test
    fun `intra-transition read after single assignment sees exactly that assignment`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var source: int := 0
                var copy: int := 0
                init {
                }
                tran {
                    source := 5
                    copy := source
                }
                prop {
                    AG true
                }
            """,
        )

        val source = inlined.varNamed("source")
        val sourceWrite = inlined.assignmentsTo(source).single()

        val copyAssignment = inlined.assignmentsTo(inlined.varNamed("copy")).single()
        val sourceReadInCopy = copyAssignment.expression as ElementReference
        assertThat(sourceReadInCopy.element).isSameAs(source)

        val definitions = result.definitionsOf[sourceReadInCopy]
            ?: error("RHS read of 'source' in 'copy := source' not in defsOf map")

        assertThat(definitions).containsExactly(sourceWrite as EObject)
    }

    @Test
    fun `tran read of variable with init choice writing on every branch does not see the initializer`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var current: int := -1
                var copy: int := 0
                init {
                    choice {
                        current := 0
                    } or {
                        current := 1
                    }
                }
                tran {
                    copy := current
                }
                prop {
                    AG true
                }
            """,
        )

        val current = inlined.varNamed("current")
        val definitions = result.definitionsOf[inlined.tranReadOf(current, "copy")]!!

        val writes = inlined.assignmentsTo(current)
        assertThat(definitions).containsExactlyInAnyOrderElementsOf(
            writes.map {
                it as EObject
            },
        )
        assertThat(definitions).doesNotContain(current as EObject)
    }

    @Test
    fun `tran read of variable with init choice that has an empty branch sees the initializer`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var current: int := -1
                var copy: int := 0
                init {
                    choice {
                        current := 0
                    } or {
                    }
                }
                tran {
                    copy := current
                }
                prop {
                    AG true
                }
            """,
        )

        val current = inlined.varNamed("current")
        val definitions = result.definitionsOf[inlined.tranReadOf(current, "copy")]!!

        val write = inlined.assignmentsTo(current).single()
        assertThat(definitions).containsExactlyInAnyOrder(write as EObject, current as EObject)
    }

    @Test
    fun `init if without an else is not a must-write`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var source: int := 0
                var copy: int := 0
                init {
                    if (true) {
                        source := 1
                    }
                }
                tran {
                    copy := source
                }
                prop {
                    AG true
                }
            """,
        )

        val source = inlined.varNamed("source")
        val definitions = result.definitionsOf[inlined.tranReadOf(source, "copy")]!!

        val initWrite = inlined.assignmentsTo(source).single()
        assertThat(definitions).containsExactlyInAnyOrder(initWrite as EObject, source as EObject)
    }

    @Test
    fun `init if with both branches writing the variable is a must-write`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var source: int := 0
                var copy: int := 0
                init {
                    if (true) {
                        source := 1
                    } else {
                        source := 2
                    }
                }
                tran {
                    copy := source
                }
                prop {
                    AG true
                }
            """,
        )

        val source = inlined.varNamed("source")
        val definitions = result.definitionsOf[inlined.tranReadOf(source, "copy")]!!

        val writes = inlined.assignmentsTo(source)
        assertThat(definitions).containsExactlyInAnyOrderElementsOf(
            writes.map {
                it as EObject
            },
        )
        assertThat(definitions).doesNotContain(source as EObject)
    }

    @Test
    fun `havoc counts as a reaching definition - and the initializer reaches when init does not write`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var value: int := 0
                init {
                }
                tran {
                    havoc(value)
                }
                prop {
                    AG (value != 27)
                }
            """,
        )

        val value = inlined.varNamed("value")
        val propertyRead = inlined.findPropertyReadOf(value)
        val definitions = result.definitionsOf[propertyRead]!!

        val havoc = inlined.havocsOn(value).single()
        assertThat(definitions).containsExactlyInAnyOrder(havoc as EObject, value as EObject)
    }

    @Test
    fun `guard read in assume sees reaching definitions at that program point`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var source: int := 0
                var other: int := 0
                init {
                }
                tran {
                    source := 5
                    assume(source == 5)
                    other := 1
                }
                prop {
                    AG true
                }
            """,
        )

        val source = inlined.varNamed("source")
        val sourceWrite = inlined.assignmentsTo(source).single()

        val assume = inlined.assumesReading(source).single()
        val sourceReadInGuard = (assume.expression as ComparisonOperator).left as ElementReference

        val definitions = result.definitionsOf[sourceReadInGuard] ?: error("Read of 'source' inside assume guard not in defsOf map")

        assertThat(definitions).containsExactly(sourceWrite as EObject)
    }

    @Test
    fun `state-machine-style consecutive writes all reach the property read`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var activeState: int := -1
                init {
                    activeState := 0
                }
                tran {
                    choice {
                        assume(activeState == 0)
                        activeState := -1
                        activeState := 25
                    }
                }
                prop {
                    AG (activeState != 25)
                }
            """,
        )

        val activeState = inlined.varNamed("activeState")
        val propertyRead = inlined.findPropertyReadOf(activeState)
        val definitions = result.definitionsOf[propertyRead]!!

        val writes = inlined.assignmentsTo(activeState)
        assertThat(writes).hasSize(3) // init + two inside the branch
        for (write in writes) {
            assertThat(definitions)
                .`as`("property read should see write $write")
                .contains(write as EObject)
        }
    }

    @Test
    fun `init read after a write on one branch also sees the declaration`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var condition: bool := false
                var source: int := 0
                var copy: int := 0
                init {
                    if (condition) {
                        source := 1
                    }
                    copy := source
                }
                tran {
                }
                prop {
                    AG true
                }
            """,
        )

        val source = inlined.varNamed("source")
        val definitions = result.definitionsOf[inlined.tranReadOf(source, "copy")]!!

        val initWrite = inlined.assignmentsTo(source).single()
        assertThat(definitions).containsExactlyInAnyOrder(initWrite as EObject, source as EObject)
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull {
            it.name == name
        } ?: error("No variable named '$name' in inlined oxsts")
    }

    private fun InlinedOxsts.assignmentsTo(variable: VariableDeclaration): List<AssignmentOperation> {
        return eAllOfType<AssignmentOperation>().filter {
            val reference = it.reference
            reference is ElementReference && reference.element === variable
        }.toList()
    }

    private fun InlinedOxsts.havocsOn(variable: VariableDeclaration): List<HavocOperation> {
        return eAllOfType<HavocOperation>().filter {
            val reference = it.reference
            reference is ElementReference && reference.element === variable
        }.toList()
    }

    private fun InlinedOxsts.assumesReading(variable: VariableDeclaration): List<AssumptionOperation> {
        return eAllOfType<AssumptionOperation>().filter {
            it.expression.eAllOfType<ElementReference>().any {
                it.element === variable
            }
        }.toList()
    }

    private fun InlinedOxsts.tranReadOf(variable: VariableDeclaration, target: String): Expression {
        val read = assignmentsTo(varNamed(target)).single().expression as ElementReference
        assertThat(read.element).isSameAs(variable)
        return read
    }

    private fun InlinedOxsts.findPropertyReadOf(variable: VariableDeclaration): Expression {
        val property = eAllOfType<PropertyDeclaration>().first()
        return property.expression.eAllOfType<ElementReference>().firstOrNull {
            it.element === variable
        } ?: error("Property does not reference '${variable.name}'")
    }
}
