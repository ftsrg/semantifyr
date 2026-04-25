/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.junit.jupiter.api.Test

class LocalVarSemanticsTest : AnalysisTestBase() {
    @Test
    fun `local var declaration is recognized as a variable`() {
        val (inlined, result) = runLiveness(
            """
                inlined oxsts of semantifyr::Anything
                init { }
                tran {
                    var x : int := 5
                    assume(x == 5)
                }
                prop { AG true }
            """,
        )
        val local: VariableDeclaration = inlined.localVarNamed("x")
        assertThat(result.isRead(local)).isTrue
    }

    @Test
    fun `local var with only an initializer is not seen as constant here`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                init { }
                tran {
                    var x : int := 5
                    assume(x == 5)
                }
                prop { AG true }
            """,
        )
        val local: VariableDeclaration = inlined.localVarNamed("x")
        assertThat(result.isConstant(local)).isFalse
    }

    @Test
    fun `local var assignment after declaration kills the declaration as reaching def`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var y : int := 0
                init { }
                tran {
                    var x : int := 5
                    x := 10
                    y := x
                }
                prop { AG true }
            """,
        )
        val x = inlined.localVarNamed("x")
        val y = inlined.varNamed("y")

        val xWrite = inlined.assignmentsTo(x).single()
        val yWrite = inlined.assignmentsTo(y).single()
        val xReadInYRhs = yWrite.expression as ElementReference

        assertThat(result.defsOf[xReadInYRhs]!!).containsExactly(xWrite as EObject)
    }

    @Test
    fun `local var read right after declaration sees only the declaration`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var y : int := 0
                init { }
                tran {
                    var x : int := 5
                    y := x
                    x := 10
                }
                prop { AG true }
            """,
        )
        val x = inlined.localVarNamed("x")
        val y = inlined.varNamed("y")
        val yWrite = inlined.assignmentsTo(y).single()
        val xReadInYRhs = yWrite.expression as ElementReference

        val defs = result.defsOf[xReadInYRhs]!!
        assertThat(defs).containsExactly(x as EObject)
    }

    @Test
    fun `gamma-style anyRegionExecuted pattern keeps every region's 'true' write relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var activeState : int := -1
                init { activeState := 0 }
                tran {
                    var anyRegionExecuted : bool := false
                    choice {
                        assume(activeState == 0)
                        activeState := 25
                        anyRegionExecuted := true
                    } or {
                        assume(activeState == 25)
                        activeState := 0
                        anyRegionExecuted := true
                    }
                    assume(anyRegionExecuted)
                }
                prop { AG (activeState != 25) }
            """,
        )

        val activeState = inlined.varNamed("activeState")
        val anyRegionExecuted = inlined.localVarNamed("anyRegionExecuted")

        assertThat(result.isRelevant(activeState)).isTrue
        assertThat(result.isRelevant(anyRegionExecuted as VariableDeclaration))
            .`as`("the local 'anyRegionExecuted' flag guards a relevant write chain and must be in the cone")
            .isTrue

        for (write in inlined.assignmentsTo(activeState)) {
            assertThat(result.isRelevant(write))
                .`as`("every write to 'activeState' should stay relevant")
                .isTrue
        }
        for (flagWrite in inlined.assignmentsTo(anyRegionExecuted)) {
            assertThat(result.isRelevant(flagWrite))
                .`as`("every write to the local flag should stay relevant")
                .isTrue
        }
    }

    @Test
    fun `local var with no assignments - only its declaration contributes a def`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var y : int := 0
                init { }
                tran {
                    var x : int := 5
                    y := x
                }
                prop { AG true }
            """,
        )
        val x = inlined.localVarNamed("x")
        val yWrite = inlined.assignmentsTo(inlined.varNamed("y")).single()
        val xRead = yWrite.expression as ElementReference

        // No assignments to x, so the only def is the declaration itself.
        assertThat(result.defsOf[xRead]!!).containsExactly(x as EObject)
    }

    @Test
    fun `two separately-declared local vars with the same name are different variables`() {
        val (inlined, result) = runLiveness(
            """
                inlined oxsts of semantifyr::Anything
                init { }
                tran {
                    choice {
                        var x : int := 1
                        assume(x == 1)
                    } or {
                        var x : int := 2
                        assume(x == 2)
                    }
                }
                prop { AG true }
            """,
        )
        val locals = inlined.eAllOfType<LocalVarDeclarationOperation>().toList()
        assertThat(locals).hasSize(2)
        for (local in locals) {
            assertThat(result.isRead(local))
                .`as`("each local $local is read by its own assume guard")
                .isTrue
        }
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull { it.name == name }
            ?: error("No variable named '$name'")
    }

    private fun InlinedOxsts.localVarNamed(name: String): LocalVarDeclarationOperation {
        return eAllOfType<LocalVarDeclarationOperation>().firstOrNull { it.name == name }
            ?: error("No local var named '$name'")
    }

    private fun InlinedOxsts.assignmentsTo(variable: VariableDeclaration): List<AssignmentOperation> {
        return eAllOfType<AssignmentOperation>()
            .filter {
                val ref = it.reference
                ref is ElementReference && ref.element === variable
            }.toList()
    }
}
