/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.sources

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.benchmark.verification.BenchmarkCase
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

@InjectWithOxsts
class SysMLCorpusSourceTest {

    @Inject
    private lateinit var injector: Injector

    private val source by lazy {
        SysMLCorpusSource(injector, Path("../frontends/sysml/models/examples"))
    }

    @Test
    fun `the manifest gives the models, expectations, twins and tiers`() {
        assertThat(source.model("crossroads.sysml").cases).contains(
            BenchmarkCase("ControllerInterruptedUnreachableViolated", VerificationVerdict.Failed, falsification = true, nightly = false),
            BenchmarkCase("ControllerOperatingReachable", VerificationVerdict.Passed, falsification = false, nightly = true),
        )
        assertThat(source.model("door_access.sysml").cases).contains(
            BenchmarkCase("LockedReachable", VerificationVerdict.Passed, falsification = false, nightly = false),
        )
    }

    @Test
    fun `a case without a known verdict has no expectation`() {
        assertThat(source.model("spacecraft.sysml").cases).contains(
            BenchmarkCase("StationBehaviorIdleReachable", null, falsification = false, nightly = true),
        )
    }

    @Test
    fun `the input counters count sysml keywords outside comments`() {
        val sysml = """
            // part commented out
            part def Controller {
                exhibit state Behaviour {
                    state Idle;
                    /* state Hidden; */
                    transition first Idle accept Go then Idle;
                }
            }
            part controller : Controller;
            verification def IdleReachable;
        """.trimIndent()

        val measurement = SysMLMeasurement.count(sysml, 3.seconds)

        assertThat(measurement).isEqualTo(
            SysMLMeasurement(transformationDuration = 3.seconds, lines = 8, parts = 2, states = 2, transitions = 1, sends = 0, verificationCases = 1),
        )
    }
}
