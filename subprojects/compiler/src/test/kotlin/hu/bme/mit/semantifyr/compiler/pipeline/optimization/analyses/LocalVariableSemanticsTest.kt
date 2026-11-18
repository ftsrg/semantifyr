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

class LocalVariableSemanticsTest : AnalysisTestBase() {
    @Test
    fun `local variable with only an initializer is not seen as constant here`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                init {
                }
                tran {
                    var local: int := 5
                    assume(local == 5)
                }
                prop {
                    AG true
                }
            """,
        )
        val local: VariableDeclaration = inlined.localVarNamed("local")
        assertThat(result.isConstant(local)).isFalse
    }

    @Test
    fun `local variable assignment after declaration kills the declaration as reaching definition`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var copy: int := 0
                init {
                }
                tran {
                    var local: int := 5
                    local := 10
                    copy := local
                }
                prop {
                    AG true
                }
            """,
        )
        val local = inlined.localVarNamed("local")
        val copy = inlined.varNamed("copy")

        val localWrite = inlined.assignmentsTo(local).single()
        val copyWrite = inlined.assignmentsTo(copy).single()
        val localReadInCopy = copyWrite.expression as ElementReference

        assertThat(result.definitionsOf[localReadInCopy]!!).containsExactly(localWrite as EObject)
    }

    @Test
    fun `local variable read right after declaration sees only the declaration`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var copy: int := 0
                init {
                }
                tran {
                    var local: int := 5
                    copy := local
                    local := 10
                }
                prop {
                    AG true
                }
            """,
        )
        val local = inlined.localVarNamed("local")
        val copy = inlined.varNamed("copy")
        val copyWrite = inlined.assignmentsTo(copy).single()
        val localReadInCopy = copyWrite.expression as ElementReference

        val definitions = result.definitionsOf[localReadInCopy]!!
        assertThat(definitions).containsExactly(local as EObject)
    }

    @Test
    fun `local variable with no assignments - only its declaration contributes a definition`() {
        val (inlined, result) = runReachingDefinitions(
            """
                inlined oxsts of semantifyr::Anything
                var copy: int := 0
                init { }
                tran {
                    var local: int := 5
                    copy := local
                }
                prop { AG true }
            """,
        )
        val local = inlined.localVarNamed("local")
        val copyWrite = inlined.assignmentsTo(inlined.varNamed("copy")).single()
        val localRead = copyWrite.expression as ElementReference

        assertThat(result.definitionsOf[localRead]!!).containsExactly(local as EObject)
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull {
            it.name == name
        } ?: error("No variable named '$name'")
    }

    private fun InlinedOxsts.localVarNamed(name: String): LocalVarDeclarationOperation {
        return eAllOfType<LocalVarDeclarationOperation>().firstOrNull {
            it.name == name
        } ?: error("No local var named '$name'")
    }

    private fun InlinedOxsts.assignmentsTo(variable: VariableDeclaration): List<AssignmentOperation> {
        return eAllOfType<AssignmentOperation>().filter {
            val reference = it.reference
            reference is ElementReference && reference.element === variable
        }.toList()
    }
}
