/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.verification

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.spin.ReplayingSpinExecutor
import hu.bme.mit.semantifyr.backends.spin.SpinExecutionResult
import hu.bme.mit.semantifyr.backends.spin.SpinExecutionSpecification
import hu.bme.mit.semantifyr.backends.spin.SpinExecutor
import hu.bme.mit.semantifyr.backends.spin.SpinExecutorKey
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

@InjectWithOxsts
class SpinSearchDepthTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var injector: Injector

    @Test
    suspend fun `the configured search depth reaches every search`() {
        val searches = mutableListOf<SpinExecutionSpecification>()
        val replaying = ReplayingSpinExecutor("ChoiceBound")
        val recording = object : SpinExecutor by replaying {
            override suspend fun execute(specification: SpinExecutionSpecification): SpinExecutionResult {
                searches += specification
                return replaying.execute(specification)
            }
        }
        val environment = ExecutionEnvironment.builder().put(SpinExecutorKey) {
            recording
        }.build()
        val artifactDirectory = Path("build", "test-artifacts", "SpinSearchDepthTest")
        artifactDirectory.toFile().deleteRecursively()
        val compilation = models.compileFlattened("control_flow", "ChoiceBound", artifactDirectory)
        val request = BackendVerificationRequest(compilation.inlinedOxsts, artifactDirectory, transitionCallTraces = compilation.transitionCallTraces)

        SpinBackend().verify(injector, SpinConfig.SafeDfs.copy(maxSearchDepth = 1_000_000), request, environment)

        assertThat(searches).isNotEmpty.allSatisfy {
            assertThat(it.maxSearchDepth).isEqualTo(1_000_000)
        }
    }
}
