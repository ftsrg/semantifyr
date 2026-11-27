/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import java.nio.file.Path
import java.time.Duration

class SemantifyrVerifierFactory @Inject constructor(
    private val injector: Injector,
) {

    fun create(
        portfolio: VerificationPortfolio,
        environment: ExecutionEnvironment,
        timeout: Duration,
        artifactConfig: ArtifactConfig,
        outputDirectory: Path,
        optimizationConfig: OptimizationConfig,
    ): SemantifyrVerifier {
        return SemantifyrVerifier.builder()
            .injector(injector)
            .portfolio(portfolio)
            .environment(environment)
            .timeout(timeout)
            .artifacts(artifactConfig)
            .outputDirectory(outputDirectory)
            .optimization(optimizationConfig)
            .build()
    }

}
