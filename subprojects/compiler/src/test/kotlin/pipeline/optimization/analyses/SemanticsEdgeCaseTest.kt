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
    fun `non-local variable declaration is NOT a reaching def for property reads`() {
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
        val propRead = inlined.findPropertyReadOf(a)
        assertThat(result.defsOf[propRead]!!).doesNotContain(a as EObject)
    }

    @Test
    fun `declaration without initializer is currently NOT a reaching def`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int
                init { }
                tran { a := 5 }
                prop { AG (a != 27) }
            """,
        )
        val a = inlined.varNamed("a")
        val propRead = inlined.findPropertyReadOf(a)
        val defs = result.defsOf[propRead]!!
        val aWrite = inlined.assignmentsTo(a).single()
        // Under my current model: only the tran write reaches.
        assertThat(defs).containsExactly(aWrite as EObject)
    }

    @Test
    fun `havoc kills prior defs within the same sequence`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    a := 1
                    havoc(a)
                    b := a
                }
                prop { AG true }
            """,
        )
        val a = inlined.varNamed("a")
        val havoc = inlined.havocsOn(a).single()
        val bWrite = inlined.assignmentsTo(inlined.varNamed("b")).single()
        val aReadInBRhs = bWrite.expression as ElementReference

        assertThat(result.defsOf[aReadInBRhs]!!).containsExactly(havoc as EObject)
    }

    @Test
    fun `havoc makes a variable non-constant even if all assignments agree`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 7
                init { a := 7 }
                tran {
                    havoc(a)
                    a := 7
                }
                prop { AG (a == 7) }
            """,
        )
        val a = inlined.varNamed("a")
        assertThat(result.isConstant(a)).isFalse
    }

    @Test
    fun `assume does not kill defs - the walker treats it as a no-op for RD`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    a := 5
                    assume(a == 5)
                    b := a
                }
                prop { AG true }
            """,
        )
        val a = inlined.varNamed("a")
        val aWrite = inlined.assignmentsTo(a).single()
        val bWrite = inlined.assignmentsTo(inlined.varNamed("b")).single()
        val aReadInBRhs = bWrite.expression as ElementReference

        assertThat(result.defsOf[aReadInBRhs]!!).containsExactly(aWrite as EObject)
    }

    @Test
    fun `assume false does NOT prune subsequent writes from RD`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { }
                tran {
                    assume(false)
                    a := 5
                }
                prop { AG (a != 5) }
            """,
        )
        val a = inlined.varNamed("a")
        val write = inlined.assignmentsTo(a).single()
        val propRead = inlined.findPropertyReadOf(a)
        assertThat(result.defsOf[propRead]!!).contains(write as EObject)
    }

    @Test
    fun `choice branches are joined - OUT is the union of branch OUTs`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    choice { a := 1 } or { a := 2 }
                    b := a
                }
                prop { AG true }
            """,
        )
        val a = inlined.varNamed("a")
        val aWrites = inlined.assignmentsTo(a).toSet()
        val bWrite = inlined.assignmentsTo(inlined.varNamed("b")).single()
        val aReadInBRhs = bWrite.expression as ElementReference
        val defs = result.defsOf[aReadInBRhs]!!

        assertThat(defs)
            .`as`("after a choice, both branches' writes reach the downstream read")
            .containsAll(aWrites.map { it as EObject })
    }

    @Test
    fun `if-without-else passes through the original defs on the false arm`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    a := 1
                    if (b == 0) { a := 2 }
                    b := a
                }
                prop { AG true }
            """,
        )
        val a = inlined.varNamed("a")
        val allAWrites = inlined.assignmentsTo(a).toSet()
        val bWrite = inlined.assignmentsTo(inlined.varNamed("b")).single()
        val aReadInBRhs = bWrite.expression as ElementReference
        val defs = result.defsOf[aReadInBRhs]!!
        assertThat(defs).containsAll(allAWrites.map { it as EObject })
    }

    @Test
    fun `reads in init currently see main-tran writes - sound but imprecise`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init {
                    a := b
                }
                tran {
                    b := 99
                }
                prop { AG true }
            """,
        )
        val b = inlined.varNamed("b")
        val initRead = inlined.readsOfInInit(b).single()
        val bWriteInTran = inlined.assignmentsTo(b).single()

        val defs = result.defsOf[initRead]!!
        assertThat(defs).contains(bWriteInTran as EObject)
    }

    @Test
    fun `main-tran entry sees all writes including those that appear later in the tran body`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    b := a
                    a := 5
                }
                prop { AG true }
            """,
        )
        val a = inlined.varNamed("a")
        val bWrite = inlined.assignmentsTo(inlined.varNamed("b")).single()
        val aReadInBRhs = bWrite.expression as ElementReference
        val aWrite = inlined.assignmentsTo(a).single()

        val defs = result.defsOf[aReadInBRhs]!!
        assertThat(defs).contains(aWrite as EObject)
        assertThat(defs).doesNotContain(a as EObject)
    }

    @Test
    fun `cone excludes writes to a variable that is never read anywhere`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran { b := 1 }
                prop { AG (a == 0) }
            """,
        )
        val b = inlined.varNamed("b")
        val bWrite = inlined.assignmentsTo(b).single()
        assertThat(result.isRelevant(b)).isFalse
        assertThat(result.isRelevant(bWrite)).isFalse
    }

    @Test
    fun `nested if guards contribute variables to the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                var c : int := 0
                init { }
                tran {
                    if (b == 0) {
                        if (c == 0) {
                            a := 1
                        }
                    }
                }
                prop { AG (a != 1) }
            """,
        )
        val a = inlined.varNamed("a")
        val b = inlined.varNamed("b")
        val c = inlined.varNamed("c")
        assertThat(result.isRelevant(a)).isTrue
        assertThat(result.isRelevant(b))
            .`as`("outer if-guard variable 'b' should be in the cone")
            .isTrue
        assertThat(result.isRelevant(c))
            .`as`("inner if-guard variable 'c' should be in the cone")
            .isTrue
    }

    @Test
    fun `havoc to a relevant variable is itself relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { }
                tran { havoc(a) }
                prop { AG (a != 42) }
            """,
        )
        val a = inlined.varNamed("a")
        val havoc = inlined.havocsOn(a).single()
        assertThat(result.isRelevant(havoc))
            .`as`("havoc on a property-relevant variable must be kept")
            .isTrue
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull { it.name == name }
            ?: error("No variable named '$name'")
    }

    private fun InlinedOxsts.assignmentsTo(variable: VariableDeclaration): List<AssignmentOperation> {
        return eAllOfType<AssignmentOperation>()
            .filter {
                val ref = it.reference
                ref is ElementReference && ref.element === variable
            }.toList()
    }

    private fun InlinedOxsts.havocsOn(variable: VariableDeclaration): List<HavocOperation> {
        return eAllOfType<HavocOperation>()
            .filter {
                val ref = it.reference
                ref is ElementReference && ref.element === variable
            }.toList()
    }

    private fun InlinedOxsts.readsOfInInit(variable: VariableDeclaration): List<Expression> {
        val init = eAllOfType<TransitionDeclaration>().first { it.kind == TransitionKind.INIT }
        return init
            .eAllOfType<ElementReference>()
            .filter {
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
        return property.expression
            .eAllOfType<ElementReference>()
            .firstOrNull { it.element === variable }
            ?: error("Property does not reference '${variable.name}'")
    }
}
