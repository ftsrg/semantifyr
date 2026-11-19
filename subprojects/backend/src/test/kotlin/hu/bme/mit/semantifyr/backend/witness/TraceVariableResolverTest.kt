/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@InjectWithOxsts
class TraceVariableResolverTest {

    @Inject
    private lateinit var models: BackendTestModels

    private object DeclaredNames : BackendVariableNames {
        override fun nameOf(variableDeclaration: VariableDeclaration): String {
            return variableDeclaration.name
        }
    }

    private val inlinedOxsts: InlinedOxsts by lazy {
        models.parse(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int := 0
                init {
                }
                tran {
                    var counter : int := 1
                    var step : int := counter
                }
                prop {
                    AG true
                }
            """,
        )
    }

    private fun resolver(): TraceVariableResolver {
        return TraceVariableResolver(inlinedOxsts, DeclaredNames, setOf("flag"), "the trace")
    }

    @Test
    fun `a state variable resolves to its declaration, also where a local shares its name`() {
        assertThat(resolver().stateVariableOf("counter")).isSameAs(inlinedOxsts.variables.single())
    }

    @Test
    fun `a local and a name the backend added resolve to nothing`() {
        assertThat(resolver().stateVariableOf("step")).isNull()
        assertThat(resolver().stateVariableOf("flag")).isNull()
    }

    @Test
    fun `a name of no variable fails the read-back`() {
        val failure = assertThrows<WitnessFailedException> {
            resolver().stateVariableOf("ghost")
        }

        assertThat(failure.message).isEqualTo("the trace holds ghost, which names no variable of the model")
    }
}
