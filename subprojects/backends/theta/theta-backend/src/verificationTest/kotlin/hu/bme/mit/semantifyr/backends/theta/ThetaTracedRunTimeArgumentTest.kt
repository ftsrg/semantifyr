/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.ReportValue
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationReport
import hu.bme.mit.semantifyr.verifier.assertVerdict
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.caseReportPath
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigInteger
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ThetaTracedRunTimeArgumentTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        @JvmStatic
        @BeforeAll
        fun assumeThetaAvailable() {
            assumeBackendAvailable(ThetaExecutorKey)
        }

        private val MODEL = """
            package semantifyr::verification::theta::traced_run_time_argument

            class Port {
                var item: int := 0

                @Trace
                tran send(value: int) {
                    item := value
                }
            }

            @VerificationCase
            class RunTimeArgumentReach {
                contains port: Port
                var count: int := 0

                redefine tran {
                    choice {
                        count := 1
                    } or {
                        count := 2
                    }
                    inline port.send(count)
                }

                prop {
                    return EF (port.item == 2)
                }
            }
        """.trimIndent()
    }

    @Test
    suspend fun `a traced call with a run-time argument verifies with the argument's value in its call and not in the states`(@TempDir modelDirectory: Path) {
        val modelFile = modelDirectory.resolve("traced_run_time_argument.oxsts")
        modelFile.writeText(MODEL)
        val context = helper.semantifyrLoader.loadStandaloneModel(modelFile)
        val case = helper.collectVerificationCases(context).single()
        val outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(ThetaTracedRunTimeArgumentTest::class.java)

        val result = helper.runVerificationCase(
            context = context,
            case = case,
            verificationPortfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarExplPredCombined),
            outputDirectory = outputDirectory,
        )

        assertVerdict(case.qualifiedName, VerificationVerdict.Passed, result)
        assertThat(result.witness).isNotNull()
        val trace = Json.decodeFromString<VerificationReport>(caseReportPath(outputDirectory, case).readText()).trace!!
        assertThat(trace.variables).noneSatisfy {
            assertThat(it.variable).contains(TransitionTracerNames.ARGUMENT_PREFIX)
        }
        assertThat(trace.states).allSatisfy { state ->
            assertThat(state.values.keys).noneSatisfy {
                assertThat(it).contains(TransitionTracerNames.ARGUMENT_PREFIX)
            }
        }
        assertThat(
            trace.steps.flatMap {
                it.calls
            },
        ).anySatisfy {
            assertThat(it.transition).endsWith("Port::send")
            assertThat(it.arguments).containsExactly(
                entry("value", ReportValue.Integer(BigInteger.TWO)),
            )
        }
    }
}
