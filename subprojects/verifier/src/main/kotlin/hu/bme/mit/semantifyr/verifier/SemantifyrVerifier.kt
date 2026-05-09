/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import com.google.inject.AbstractModule
import com.google.inject.Injector
import com.google.inject.assistedinject.FactoryModuleBuilder
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verifier.internal.SemantifyrVerifierConfiguration
import hu.bme.mit.semantifyr.verifier.internal.SemantifyrVerifierImpl
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toKotlinDuration

/**
 * The main Verifier entrypoint that exposes verification cases and their verification.
 */
interface SemantifyrVerifier {

    suspend fun verify(verificationCase: VerificationCase, progressContext: ProgressContext = ProgressContext.NoOp): VerificationResult
    suspend fun verify(inlinedOxsts: InlinedOxsts, progressContext: ProgressContext = ProgressContext.NoOp): VerificationResult

    fun verifyBlocking(verificationCase: VerificationCase) = runBlocking {
        verify(verificationCase).toJavaDto()
    }
    fun verifyBlocking(verificationCase: VerificationCase, progressContext: ProgressContext) = runBlocking {
        verify(verificationCase, progressContext).toJavaDto()
    }
    fun verifyBlocking(inlinedOxsts: InlinedOxsts) = runBlocking {
        verify(inlinedOxsts).toJavaDto()
    }
    fun verifyBlocking(inlinedOxsts: InlinedOxsts, progressContext: ProgressContext) = runBlocking {
        verify(inlinedOxsts, progressContext).toJavaDto()
    }

    class Builder internal constructor() {
        private var injector: Injector? = null
        private var verificationPortfolio: VerificationPortfolio? = null
        private var artifacts: ArtifactConfig? = null
        private var outputDirectory: Path? = null
        private var environment = ExecutionEnvironment.Empty
        private var timeout = 5.minutes
        private var maxConcurrency = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        private var optimization = OptimizationConfig.DEFAULT

        fun injector(injector: Injector): Builder {
            this.injector = injector
            return this
        }

        fun portfolio(verificationPortfolio: VerificationPortfolio): Builder {
            this.verificationPortfolio = verificationPortfolio
            return this
        }

        fun artifacts(config: ArtifactConfig): Builder {
            this.artifacts = config
            return this
        }

        fun outputDirectory(path: Path): Builder {
            this.outputDirectory = path
            return this
        }

        fun environment(environment: ExecutionEnvironment): Builder {
            this.environment = environment
            return this
        }

        fun timeout(timeout: Duration): Builder {
            require(timeout.isPositive()) { "timeout must be positive, got $timeout" }
            this.timeout = timeout
            return this
        }

        fun timeout(timeout: java.time.Duration): Builder {
            return timeout(timeout.toKotlinDuration())
        }

        fun maxConcurrency(limit: Int): Builder {
            require(limit >= 1) { "maxConcurrency must be >= 1" }
            this.maxConcurrency = limit
            return this
        }

        fun optimization(config: OptimizationConfig): Builder {
            this.optimization = config
            return this
        }

        fun build(): SemantifyrVerifier {
            val resolvedPortfolio = requireNotNull(verificationPortfolio) {
                "SemantifyrVerifier.Builder requires .portfolio(...)."
            }
            val resolvedArtifacts = requireNotNull(artifacts) {
                "SemantifyrVerifier.Builder requires .artifacts(...)."
            }
            val resolvedOutputDirectory = requireNotNull(outputDirectory) {
                "SemantifyrVerifier.Builder requires .outputDirectory(...)."
            }

            val resolvedInjector = injector ?: OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
            val verifierInjector = resolvedInjector.createChildInjector(SemantifyrVerifierModule())

            val configuration = SemantifyrVerifierConfiguration(
                injector = verifierInjector,
                portfolio = resolvedPortfolio,
                artifactConfig = resolvedArtifacts,
                outputDirectory = resolvedOutputDirectory,
                environment = environment,
                timeout = timeout,
                maxConcurrency = maxConcurrency,
                optimizationConfig = optimization,
            )

            val semantifyrVerifierImplFactory = verifierInjector.getInstance(SemantifyrVerifierImpl.Factory::class.java)

            return semantifyrVerifierImplFactory.create(configuration)
        }
    }

    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }
}

class SemantifyrVerifierModule : AbstractModule() {
    override fun configure() {
        install(FactoryModuleBuilder().build(SemantifyrVerifierImpl.Factory::class.java))

        super.configure()
    }
}
