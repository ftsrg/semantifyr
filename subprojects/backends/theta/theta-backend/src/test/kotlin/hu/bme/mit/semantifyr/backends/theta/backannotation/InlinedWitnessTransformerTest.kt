/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScopeBlocking
import hu.bme.mit.semantifyr.backend.transformation.WHOLE_DELAY_VARIABLE_NAME
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backends.theta.ThetaBackendModule
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.ThetaVariableTransformer
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.XstsFactory
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

@InjectWithOxsts
class InlinedWitnessTransformerTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var oxstsInjector: Injector

    private val inlinedOxsts: InlinedOxsts by lazy {
        models.parse(BackendTestModels.SATURATING_COUNTER_EXCEEDS_BOUND)
    }

    private fun transform(values: (String) -> Map<String, Int>): InlinedWitness {
        return withVerificationScopeBlocking {
            val injector = oxstsInjector.createChildInjector(ThetaBackendModule())
            val counter = injector.getInstance(ThetaVariableTransformer::class.java).nameOf(inlinedOxsts.variables.single())
            val xsts = XstsFactory.createXstsModel()
            val state = XstsWitnessState(
                values(counter).map { (name, value) ->
                    val declaration = XstsFactory.createTopLevelVariableDeclaration().also {
                        it.name = name
                        it.type = XstsFactory.createIntegerType()
                    }
                    xsts.variableDeclarations += declaration
                    XstsWitnessStateVariableValue(
                        declaration,
                        XstsFactory.createLiteralInteger().also {
                            it.value = value
                        },
                    )
                },
            )
            val witness = XstsWitness(state, listOf(state), xsts)
            injector.getInstance(InlinedWitnessTransformer::class.java).transform(inlinedOxsts, witness, emptySet())
        }
    }

    @Test
    fun `the delay of a step is its elapsed time and no state value`() {
        val witness = transform {
            mapOf(
                it to 1,
                WHOLE_DELAY_VARIABLE_NAME to 2,
            )
        }

        val step = witness.transitionStates.single()
        assertThat(step.elapsed).isEqualTo(Fraction.of(2))
        assertThat(
            step.values.map {
                it.variable
            },
        ).containsExactlyElementsOf(inlinedOxsts.variables)
    }

    @Test
    fun `a name of no variable fails the read-back`() {
        val failure = assertThrows<WitnessFailedException> {
            transform {
                mapOf(
                    "ghost" to 0,
                )
            }
        }

        assertThat(failure.message).contains("ghost, which names no variable of the model")
    }

    @Test
    fun `a counterexample that does not parse fails the read-back`(@TempDir directory: Path) {
        val cexFile = directory.resolve("out.cex")
        cexFile.writeText(
            """
                (XstsStateSequence
                  (XstsState pre_init last_env (ExplState (counter 0)))
                  (XstsState post_init last_internal (ExplState (counter #b1))))
            """.trimIndent(),
        )

        val failure = assertThrows<WitnessFailedException> {
            CexReader().loadCexModel(cexFile)
        }

        assertThat(failure.message).contains("(counter #b1)")
    }
}
