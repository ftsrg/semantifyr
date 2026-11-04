/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.junit.jupiter.api.Test

class SemanticsEdgeCaseTest : AnalysisTestBase() {
    @Test
    fun `variable declaration is a reaching definition for property reads although init overwrites it`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var value : int := 0
                init { value := 1 }
                tran { }
                prop { AG (value != 27) }
            """,
        )
        val value = inlined.varNamed("value")
        val propertyRead = inlined.findPropertyReadOf(value)
        assertThat(result.definitionsOf[propertyRead]!!).contains(value as EObject)
    }

    @Test
    fun `declaration without initializer counts the implicit havoc as a reaching definition`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var value : int
                init { }
                tran { value := 5 }
                prop { AG (value != 27) }
            """,
        )
        val value = inlined.varNamed("value")
        val propertyRead = inlined.findPropertyReadOf(value)
        val definitions = result.definitionsOf[propertyRead]!!
        val valueWrite = inlined.assignmentsTo(value).single()
        assertThat(definitions).containsExactlyInAnyOrder(valueWrite as EObject, value as EObject)
    }

    @Test
    fun `havoc kills prior definitions within the same sequence`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var source : int := 0
                var target : int := 0
                init { }
                tran {
                    source := 1
                    havoc(source)
                    target := source
                }
                prop { AG true }
            """,
        )
        val source = inlined.varNamed("source")
        val havoc = inlined.havocsOn(source).single()
        val targetWrite = inlined.assignmentsTo(inlined.varNamed("target")).single()
        val sourceReadInTarget = targetWrite.expression as ElementReference

        assertThat(result.definitionsOf[sourceReadInTarget]!!).containsExactly(havoc as EObject)
    }

    @Test
    fun `havoc makes a variable non-constant even if all assignments agree`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var value : int := 7
                init { value := 7 }
                tran {
                    havoc(value)
                    value := 7
                }
                prop { AG (value == 7) }
            """,
        )
        val value = inlined.varNamed("value")
        assertThat(result.isConstant(value)).isFalse
    }

    @Test
    fun `assume does not kill definitions - the walker treats it as a no-op for reaching definitions`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var source : int := 0
                var target : int := 0
                init { }
                tran {
                    source := 5
                    assume(source == 5)
                    target := source
                }
                prop { AG true }
            """,
        )
        val source = inlined.varNamed("source")
        val sourceWrite = inlined.assignmentsTo(source).single()
        val targetWrite = inlined.assignmentsTo(inlined.varNamed("target")).single()
        val sourceReadInTarget = targetWrite.expression as ElementReference

        assertThat(result.definitionsOf[sourceReadInTarget]!!).containsExactly(sourceWrite as EObject)
    }

    @Test
    fun `assume false does NOT prune subsequent writes from reaching definitions`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var value : int := 0
                init { }
                tran {
                    assume(false)
                    value := 5
                }
                prop { AG (value != 5) }
            """,
        )
        val value = inlined.varNamed("value")
        val write = inlined.assignmentsTo(value).single()
        val propertyRead = inlined.findPropertyReadOf(value)
        assertThat(result.definitionsOf[propertyRead]!!).contains(write as EObject)
    }

    @Test
    fun `choice branches are joined - OUT is the union of branch OUTs`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var source : int := 0
                var target : int := 0
                init { }
                tran {
                    choice { source := 1 } or { source := 2 }
                    target := source
                }
                prop { AG true }
            """,
        )
        val source = inlined.varNamed("source")
        val sourceWrites = inlined.assignmentsTo(source).toSet()
        val targetWrite = inlined.assignmentsTo(inlined.varNamed("target")).single()
        val sourceReadInTarget = targetWrite.expression as ElementReference
        val definitions = result.definitionsOf[sourceReadInTarget]!!

        assertThat(definitions)
            .`as`("after a choice, both branches' writes reach the downstream read")
            .containsAll(
                sourceWrites.map {
                    it as EObject
                },
            )
    }

    @Test
    fun `if-without-else passes through the original definitions on the false arm`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var source : int := 0
                var target : int := 0
                init { }
                tran {
                    source := 1
                    if (target == 0) { source := 2 }
                    target := source
                }
                prop { AG true }
            """,
        )
        val source = inlined.varNamed("source")
        val allSourceWrites = inlined.assignmentsTo(source).toSet()
        val targetWrite = inlined.assignmentsTo(inlined.varNamed("target")).single()
        val sourceReadInTarget = targetWrite.expression as ElementReference
        val definitions = result.definitionsOf[sourceReadInTarget]!!
        assertThat(definitions).containsAll(
            allSourceWrites.map {
                it as EObject
            },
        )
    }

    @Test
    fun `reads in init do not see main-tran writes`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var copy : int := 0
                var source : int := 0
                init {
                    copy := source
                }
                tran {
                    source := 99
                }
                prop { AG true }
            """,
        )
        val source = inlined.varNamed("source")
        val initRead = inlined.readsOfInInit(source).single()
        val sourceWriteInTran = inlined.assignmentsTo(source).single()

        val definitions = result.definitionsOf[initRead]!!
        assertThat(definitions).doesNotContain(sourceWriteInTran as EObject)
        assertThat(definitions).containsExactly(source as EObject)
    }

    @Test
    fun `main-tran entry sees all writes including those that appear later in the tran body`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var source : int := 0
                var target : int := 0
                init { }
                tran {
                    target := source
                    source := 5
                }
                prop { AG true }
            """,
        )
        val source = inlined.varNamed("source")
        val targetWrite = inlined.assignmentsTo(inlined.varNamed("target")).single()
        val sourceReadInTarget = targetWrite.expression as ElementReference
        val sourceWrite = inlined.assignmentsTo(source).single()

        val definitions = result.definitionsOf[sourceReadInTarget]!!
        assertThat(definitions).contains(sourceWrite as EObject)
        assertThat(definitions).contains(source as EObject)
    }

    @Test
    fun `cone excludes writes to a variable that is never read anywhere`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var observed : int := 0
                var unread : int := 0
                init { }
                tran { unread := 1 }
                prop { AG (observed == 0) }
            """,
        )
        val unread = inlined.varNamed("unread")
        val unreadWrite = inlined.assignmentsTo(unread).single()
        assertThat(result.isRelevant(unread)).isFalse
        assertThat(result.isRelevant(unreadWrite)).isFalse
    }

    @Test
    fun `nested if guards contribute variables to the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var value : int := 0
                var outerGuard : int := 0
                var innerGuard : int := 0
                init { }
                tran {
                    if (outerGuard == 0) {
                        if (innerGuard == 0) {
                            value := 1
                        }
                    }
                }
                prop { AG (value != 1) }
            """,
        )
        val value = inlined.varNamed("value")
        val outerGuard = inlined.varNamed("outerGuard")
        val innerGuard = inlined.varNamed("innerGuard")
        assertThat(result.isRelevant(value)).isTrue
        assertThat(result.isRelevant(outerGuard))
            .`as`("outer if-guard variable 'outerGuard' should be in the cone")
            .isTrue
        assertThat(result.isRelevant(innerGuard))
            .`as`("inner if-guard variable 'innerGuard' should be in the cone")
            .isTrue
    }

    @Test
    fun `havoc to a relevant variable is itself relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var value : int := 0
                init { }
                tran { havoc(value) }
                prop { AG (value != 42) }
            """,
        )
        val value = inlined.varNamed("value")
        val havoc = inlined.havocsOn(value).single()
        assertThat(result.isRelevant(havoc))
            .`as`("havoc on a property-relevant variable must be kept")
            .isTrue
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull {
            it.name == name
        } ?: error("No variable named '$name'")
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

    private fun InlinedOxsts.readsOfInInit(variable: VariableDeclaration): List<Expression> {
        val initTransition = eAllOfType<TransitionDeclaration>().first {
            it.kind == TransitionKind.INIT
        }
        return initTransition.eAllOfType<ElementReference>().filter {
            it.element === variable
        }.filterNot {
            val parent = it.eContainer()
            parent is AssignmentOperation && parent.reference === it
        }.filterNot {
            val parent = it.eContainer()
            parent is HavocOperation && parent.reference === it
        }.toList()
    }

    private fun InlinedOxsts.findPropertyReadOf(variable: VariableDeclaration): Expression {
        val property = eAllOfType<PropertyDeclaration>().first()
        return property.expression.eAllOfType<ElementReference>().firstOrNull {
            it.element === variable
        } ?: error("Property does not reference '${variable.name}'")
    }
}
