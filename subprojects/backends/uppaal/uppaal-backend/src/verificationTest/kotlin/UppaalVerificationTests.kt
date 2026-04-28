/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutorSpec
import hu.bme.mit.semantifyr.backends.uppaal.execution.ShellBasedUppaalExecutor
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.backends.uppaal.verification.uppaal
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.verification.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verification.SingleBackendPortfolio
import hu.bme.mit.semantifyr.verification.discovery.CaseFilter
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

@InjectWithOxsts
class UppaalVerificationTests {
    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val timedModel by lazy {
            helper.semantifyrLoader
                .startContext()
                .loadModel(Path("test-models/Timed/timed.oxsts"))
                .buildAndResolve()
        }

        private val environment = ExecutionEnvironment
            .builder()
            .uppaal(UppaalExecutorSpec.Auto)
            .build()

        private val SMOKE_TIMEOUT = 30.seconds

        @JvmStatic
        @BeforeAll
        fun assumeVerifytaAvailable() {
            Assumptions.assumeTrue(
                ShellBasedUppaalExecutor().isAvailable(),
                "verifyta not on PATH - skipping Uppaal backend tests",
            )
        }

        @JvmStatic
        fun `Timed passing cases across all configs`(): List<Arguments> {
            val cases = helper.collectVerificationCases(timedModel, CaseFilter.tagged("expect-pass"))
            return helper.productAsArguments(cases, UppaalConfig.Builtin) { it.id }
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Timed passing cases across all configs`(
        verificationCase: VerificationCase,
        config: UppaalConfig,
    ) {
        helper.checkVerificationCase(
            timedModel,
            verificationCase,
            SingleBackendPortfolio(UppaalBackend, config, config.id),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(UppaalVerificationTests::class.java).resolve(config.id),
            timeout = SMOKE_TIMEOUT,
            environment = environment,
            expectedVerdict = VerificationVerdict.Passed,
        )
    }
}
