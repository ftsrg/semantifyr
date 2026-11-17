/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.clock

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

@InjectWithOxsts
class WitnessTimeScalerTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var loader: SemantifyrLoader

    @Inject
    private lateinit var serializer: ExpressionSerializer

    @Test
    fun `a witness's time scale multiplies the model's clock bounds and leaves the witness's own assumptions as written`(@TempDir directory: Path) {
        val modelPath = directory.resolve("timer.semantifyr")
        modelPath.writeText(MODEL)
        val witnessPath = directory.resolve("witness.semantifyr")
        witnessPath.writeText(WITNESS)
        val witness = loader.startContext()
            .loadModel(modelPath)
            .loadModel(witnessPath)
            .buildAndResolve()
            .modelResources
            .last()
            .contents
            .filterIsInstance<InlinedOxsts>()
            .single()

        val compiled = SemantifyrCompiler(injector, ArtifactConfig.NONE, OptimizationConfig.NONE).compile(witness, directory.resolve("compiled"))

        val comparisons = compiled.inlinedOxsts.eAllOfType<ComparisonOperator>().map {
            serializer.serialize(it).trim()
        }.toList()
        assertThat(comparisons).anySatisfy {
            assertThat(it).endsWith(">= 3 * 2")
        }
        assertThat(comparisons).contains("time == 7")
        assertThat(comparisons).noneSatisfy {
            assertThat(it).endsWith("7 * 2")
        }
    }

    companion object {
        private val MODEL = """
            package clock::tests::scale

            class Timer {
                var elapsed: clock := 0
                var fired: bool := false

                tran fire() {
                    assume(elapsed >= 3)
                    fired := true
                }
            }

            @VerificationCase
            class TimerFires {
                contains timer: Timer

                redefine tran {
                    inline timer.fire()
                }

                prop {
                    return EF (timer.fired)
                }
            }
        """.trimIndent()

        private val WITNESS = """
            witness of clock::tests::scale::TimerFires timescale 2

            var step: int := 0

            var time: clock := 0

            contains root: clock::tests::scale::TimerFires

            init {
                assume(step == 0)
                inline root.init()
                assume(root.timer.fired == false)
                step := 1
            }

            tran {
                assume(step == 1)
                inline root.main()
                assume(time == 7)
                assume(root.timer.elapsed == 7)
                assume(root.timer.fired == true)
                step := 2
            }

            prop {
                root.prop()
            }
        """.trimIndent()
    }
}
