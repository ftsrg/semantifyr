/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.nio.file.Paths

class SemantifyrVerifierBuilderTest {

    private val portfolio = ErroringPortfolio()
    private val dummyArtifacts: ArtifactConfig = ArtifactConfig.none(Paths.get(System.getProperty("java.io.tmpdir")))

    @Test
    fun `verifier builder requires a context`() {
        assertThatThrownBy {
            SemantifyrVerifier.builder()
                .portfolio(portfolio)
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
                .portfolio(portfolio)
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
