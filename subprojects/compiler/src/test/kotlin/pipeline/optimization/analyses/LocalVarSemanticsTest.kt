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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.junit.jupiter.api.Test

/**
 * Tests that document how I believe local variables interact with the
 * analyses. Local vars (`LocalVarDeclarationOperation`) are declared inside
 * a transition body and - under my mental model - have these properties:
 *
 *  1. They are fresh at each iteration of a looping transition: their value
 *     at the declaration point is the initializer, never a value from a
 *     previous iteration of the enclosing tran.
 *  2. Their scope is the enclosing sequence; references outside that block
 *     are invalid models.
 *  3. A local var is both a `VariableDeclaration` (so the analyses treat it
 *     as a variable with writes and reads) and an `Operation` (so it appears
 *     in the transition body's sequence of steps).
 *
 * If any assertion below is wrong against OXSTS semantics, the analysis
 * handling is wrong in a corresponding way.
 */
class LocalVarSemanticsTest : AnalysisTestBase() {

    @Test
    fun `local var declaration is recognized as a variable`() {
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                init { }
                tran {
                    var x : int := 5
                    assume(x == 5)
                }
                prop { AG true }
            """,
            analysisClass = LivenessAnalysis::class.java,
        )
        val local: VariableDeclaration = run.inlinedOxsts.localVarNamed("x")
        assertThat(run.result.isRead(local)).isTrue
    }

    @Test
    fun `local var with only an initializer is not seen as constant here`() {
        // ConstantValueAnalysis defers init-only vars to VariableLivenessPass's
        // substitution rule. Local vars with only their declarator should
        // behave the same way as class-level vars with only an initializer.
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                init { }
                tran {
                    var x : int := 5
                    assume(x == 5)
                }
                prop { AG true }
            """,
            analysisClass = ConstantValueAnalysis::class.java,
        )
        val local: VariableDeclaration = run.inlinedOxsts.localVarNamed("x")
        assertThat(run.result.isConstant(local)).isFalse
    }

    @Test
    fun `local var assignment after declaration kills the declaration as reaching def`() {
        // Assumption: within the same transition, after `var x := 5; x := 10`,
        // a subsequent read sees only the assignment - the declaration's
        // initializer no longer reaches.
        val run = runAnalysis(
            source = """
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
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val x = run.inlinedOxsts.localVarNamed("x")
        val y = run.inlinedOxsts.varNamed("y")

        val xWrite = run.inlinedOxsts.assignmentsTo(x).single()
        val yWrite = run.inlinedOxsts.assignmentsTo(y).single()
        val xReadInYRhs = yWrite.expression as ElementReference

        assertThat(run.result.defsOf[xReadInYRhs]!!).containsExactly(xWrite as EObject)
    }

    @Test
    fun `local var read right after declaration sees only the declaration`() {
        // Local variables are re-initialized every time their declaration
        // runs. A read right after `var x := 5` (before any assignment to
        // x in the same sequence) has the declaration as its sole reaching
        // def. Writes that appear later in the same transition do not reach
        // a read positioned earlier: by the time those writes execute,
        // control has already moved past the earlier read, and at the next
        // iteration the local is reset.
        val run = runAnalysis(
            source = """
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
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val x = run.inlinedOxsts.localVarNamed("x")
        val y = run.inlinedOxsts.varNamed("y")
        val yWrite = run.inlinedOxsts.assignmentsTo(y).single()
        val xReadInYRhs = yWrite.expression as ElementReference

        val defs = run.result.defsOf[xReadInYRhs]!!
        // Only the declaration reaches - the later write is not visible to
        // this earlier read.
        assertThat(defs).containsExactly(x as EObject)
    }

    @Test
    fun `gamma-style anyRegionExecuted pattern keeps every region's 'true' write relevant`() {
        // Gamma emits a local flag to tell the outer atomic transition
        // whether any region branch committed: if no branch marked the flag,
        // the trailing `assume` fails and the whole tran rolls back.
        // Under my model (no atomicity), every branch's write to the flag
        // is in the cone because the trailing assume reads the flag.
        val run = runAnalysis(
            source = """
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
            analysisClass = ConeOfInfluenceAnalysis::class.java,
        )

        val activeState = run.inlinedOxsts.varNamed("activeState")
        val anyRegionExecuted = run.inlinedOxsts.localVarNamed("anyRegionExecuted")

        assertThat(run.result.isRelevant(activeState)).isTrue
        assertThat(run.result.isRelevant(anyRegionExecuted as VariableDeclaration))
            .`as`("the local 'anyRegionExecuted' flag guards a relevant write chain and must be in the cone")
            .isTrue

        for (write in run.inlinedOxsts.assignmentsTo(activeState)) {
            assertThat(run.result.isRelevant(write))
                .`as`("every write to 'activeState' should stay relevant")
                .isTrue
        }
        for (flagWrite in run.inlinedOxsts.assignmentsTo(anyRegionExecuted)) {
            assertThat(run.result.isRelevant(flagWrite))
                .`as`("every write to the local flag should stay relevant")
                .isTrue
        }
    }

    @Test
    fun `local var with no assignments - only its declaration contributes a def`() {
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var y : int := 0
                init { }
                tran {
                    var x : int := 5
                    y := x
                }
                prop { AG true }
            """,
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val x = run.inlinedOxsts.localVarNamed("x")
        val yWrite = run.inlinedOxsts.assignmentsTo(run.inlinedOxsts.varNamed("y")).single()
        val xRead = yWrite.expression as ElementReference

        // No assignments to x, so the only def is the declaration itself.
        assertThat(run.result.defsOf[xRead]!!).containsExactly(x as EObject)
    }

    @Test
    fun `two separately-declared local vars with the same name are different variables`() {
        // Each LocalVarDeclarationOperation is a distinct VariableDeclaration
        // regardless of textual name, so writes in one branch do not group
        // with writes in another branch.
        val run = runAnalysis(
            source = """
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
            analysisClass = LivenessAnalysis::class.java,
        )
        val locals = run.inlinedOxsts.eAllOfType<LocalVarDeclarationOperation>().toList()
        assertThat(locals).hasSize(2)
        for (local in locals) {
            assertThat(run.result.isRead(local))
                .`as`("each local $local is read by its own assume guard")
                .isTrue
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull { it.name == name }
            ?: error("No variable named '$name'")
    }

    private fun InlinedOxsts.localVarNamed(name: String): LocalVarDeclarationOperation {
        return eAllOfType<LocalVarDeclarationOperation>().firstOrNull { it.name == name }
            ?: error("No local var named '$name'")
    }

    private fun InlinedOxsts.assignmentsTo(variable: VariableDeclaration): List<AssignmentOperation> {
        return eAllOfType<AssignmentOperation>().filter { op ->
            val ref = op.reference
            ref is ElementReference && ref.element === variable
        }.toList()
    }
}
