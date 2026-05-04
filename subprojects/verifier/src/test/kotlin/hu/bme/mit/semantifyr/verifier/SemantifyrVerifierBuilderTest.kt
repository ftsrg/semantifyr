/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class SemantifyrVerifierBuilderTest {

    private val portfolio = ErroringPortfolio()
    private val dummyArtifacts: ArtifactConfig = ArtifactConfig.NONE
    private val dummyOutputDirectory = Paths.get(System.getProperty("java.io.tmpdir"))

    @Test
    fun `verifier builder requires a portfolio`() {
        assertThatThrownBy {
            SemantifyrVerifier.builder()
                .artifacts(dummyArtifacts)
                .outputDirectory(dummyOutputDirectory)
                .build()
        }.hasMessageContaining(".portfolio(...)")
    }

    @Test
    fun `verifier builder requires artifacts`() {
        assertThatThrownBy {
            SemantifyrVerifier.builder()
                .portfolio(portfolio)
                .outputDirectory(dummyOutputDirectory)
                .build()
        }.hasMessageContaining(".artifacts(...)")
    }

    @Test
    fun `verifier builder requires an outputDirectory`() {
        assertThatThrownBy {
            SemantifyrVerifier.builder()
                .portfolio(portfolio)
                .artifacts(dummyArtifacts)
                .build()
        }.hasMessageContaining(".outputDirectory(...)")
    }

    @Test
    fun `maxConcurrency must be at least one`() {
        assertThatThrownBy {
            SemantifyrVerifier.builder().maxConcurrency(0)
        }.hasMessageContaining("maxConcurrency must be >= 1")
    }
}
