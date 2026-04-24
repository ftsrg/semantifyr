/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verification.discovery.CaseFilter
import hu.bme.mit.semantifyr.verification.internal.SemantifyrVerifierImpl
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toKotlinDuration

/**
 * The main Verifier entrypoint that exposes verification cases and their verification.
 */
interface SemantifyrVerifier : AutoCloseable {

    fun modelContext(): SemantifyrModelContext
    fun verificationCases(filter: CaseFilter = CaseFilter.All): List<VerificationCase>

    suspend fun verifyAll(filter: CaseFilter = CaseFilter.All, progressContext: ProgressContext = ProgressContext.NoOp): List<VerificationResult>

    suspend fun verify(verificationCase: VerificationCase, progressContext: ProgressContext = ProgressContext.NoOp): VerificationResult

    suspend fun verify(qualifiedName: String, progressContext: ProgressContext = ProgressContext.NoOp): VerificationResult

    suspend fun verify(inlinedOxsts: InlinedOxsts, progressContext: ProgressContext = ProgressContext.NoOp): VerificationResult


    fun verifyAllBlocking(filter: CaseFilter, progressContext: ProgressContext) = runBlocking {
        verifyAll(filter, progressContext)
    }

    fun verifyAllBlocking(filter: CaseFilter = CaseFilter.All) = runBlocking {
        verifyAll(filter)
    }

    fun verifyBlocking(verificationCase: VerificationCase) = runBlocking {
        verify(verificationCase)
    }

    fun verifyBlocking(verificationCase: VerificationCase, progressContext: ProgressContext) = runBlocking {
        verify(verificationCase, progressContext)
    }

    fun verifyBlocking(qualifiedName: String) = runBlocking {
        verify(qualifiedName)
    }

    fun verifyBlocking(qualifiedName: String, progressContext: ProgressContext) = runBlocking {
        verify(qualifiedName, progressContext)
    }

    fun verifyBlocking(inlinedOxsts: InlinedOxsts) = runBlocking {
        verify(inlinedOxsts)
    }

    fun verifyBlocking(inlinedOxsts: InlinedOxsts, progressContext: ProgressContext) = runBlocking {
        verify(inlinedOxsts, progressContext)
    }

    class Builder internal constructor() {
        private var injector: Injector? = null
        private var context: SemantifyrModelContext? = null
        private var verificationPortfolio: VerificationPortfolio? = null
        private var artifacts: ArtifactConfig? = null
        private var environment = ExecutionEnvironment.Empty
        private var timeout = 5.minutes
        private var maxConcurrency = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        private var optimization = OptimizationConfig.DEFAULT

        fun injector(injector: Injector): Builder {
            this.injector = injector
            return this
        }

        fun context(context: SemantifyrModelContext): Builder {
            this.context = context
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
            val resolvedContext = requireNotNull(context) {
                "SemantifyrVerifier.Builder requires .context(...)."
            }
            val resolvedPortfolio = requireNotNull(verificationPortfolio) {
                "SemantifyrVerifier.Builder requires .portfolio(...)."
            }
            val resolvedArtifacts = requireNotNull(artifacts) {
                "SemantifyrVerifier.Builder requires .artifacts(...)."
            }
            val resolvedInjector = injector ?: OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()

            return SemantifyrVerifierImpl(
                injector = resolvedInjector,
                context = resolvedContext,
                verificationPortfolio = resolvedPortfolio,
                artifactConfig = resolvedArtifacts,
                environment = environment,
                timeout = timeout,
                maxConcurrency = maxConcurrency,
                optimizationConfig = optimization,
            )
        }
    }

    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }
}
