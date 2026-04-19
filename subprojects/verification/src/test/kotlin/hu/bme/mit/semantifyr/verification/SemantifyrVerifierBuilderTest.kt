/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.verification.portfolio.BackendExecutor
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.nio.file.Paths

class SemantifyrVerifierBuilderTest {

    private val noopPortfolio: VerificationPortfolio = object : VerificationPortfolio() {
        override val id: String = "p"
        override val displayName: String = "p"
        override val description: String = "test-only"
        override val familyId: String = "test"
        override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
            return AvailabilityReport.Available
        }
        override suspend fun verify(
            request: VerificationRequest,
            executor: BackendExecutor,
            environment: ExecutionEnvironment,
            progress: ProgressContext,
        ): VerificationResult {
            error("unused")
        }
    }

    private val dummyArtifacts: ArtifactConfig = ArtifactConfig.none(Paths.get(System.getProperty("java.io.tmpdir")))

    @Test
    fun `verifier builder requires a context`() {
        assertThatThrownBy {
            SemantifyrVerifier.builder()
                .portfolio(noopPortfolio)
                .artifacts(dummyArtifacts)
                .build()
        }.hasMessageContaining(".context(...)")
    }

    @Test
    fun `verifier builder requires a portfolio`() {
        assertThatThrownBy {
            SemantifyrVerifier.builder()
                .context(mock())
                .artifacts(dummyArtifacts)
                .build()
        }.hasMessageContaining(".portfolio(...)")
    }

    @Test
    fun `verifier builder requires artifacts`() {
        assertThatThrownBy {
            SemantifyrVerifier.builder()
                .context(mock())
                .portfolio(noopPortfolio)
                .build()
        }.hasMessageContaining(".artifacts(...)")
    }

    @Test
    fun `maxConcurrency must be at least one`() {
        assertThatThrownBy {
            SemantifyrVerifier.builder().maxConcurrency(0)
        }.hasMessageContaining("maxConcurrency must be >= 1")
    }
}
