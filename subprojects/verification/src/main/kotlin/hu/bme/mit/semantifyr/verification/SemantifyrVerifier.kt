/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
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

    suspend fun verify(
        case: VerificationCase,
        progress: ProgressContext = ProgressContext.NoOp,
    ): VerificationResult

    suspend fun verify(
        qualifiedName: String,
        progress: ProgressContext = ProgressContext.NoOp,
    ): VerificationResult

    suspend fun verifyAll(
        filter: CaseFilter = CaseFilter.All,
        progress: ProgressContext = ProgressContext.NoOp,
    ): List<VerificationResult>

    /**
     * Run the pipeline on a pre-inlined [InlinedOxsts] model and verify it.
     *
     * Primarily used for replaying counterexample witnesses: a
     * [hu.bme.mit.semantifyr.verification.trace.TraceValidator] receives the
     * verifier and calls this to re-run the witness through compilation +
     * verification. Can also be used directly when a caller already has an
     * inlined model on hand and wants to skip case discovery.
     */
    suspend fun verify(
        inlinedOxsts: InlinedOxsts,
        progress: ProgressContext = ProgressContext.NoOp,
    ): VerificationResult

    // Java-friendly blocking wrappers.

    fun verifyBlocking(case: VerificationCase): VerificationResult {
        return runBlocking { verify(case) }
    }

    fun verifyBlocking(case: VerificationCase, progress: ProgressContext): VerificationResult {
        return runBlocking { verify(case, progress) }
    }

    fun verifyBlocking(qualifiedName: String): VerificationResult {
        return runBlocking { verify(qualifiedName) }
    }

    fun verifyBlocking(qualifiedName: String, progress: ProgressContext): VerificationResult {
        return runBlocking { verify(qualifiedName, progress) }
    }

    fun verifyAllBlocking(filter: CaseFilter = CaseFilter.All): List<VerificationResult> {
        return runBlocking { verifyAll(filter) }
    }

    fun verifyAllBlocking(filter: CaseFilter, progress: ProgressContext): List<VerificationResult> {
        return runBlocking { verifyAll(filter, progress) }
    }

    fun verifyBlocking(inlinedOxsts: InlinedOxsts): VerificationResult {
        return runBlocking { verify(inlinedOxsts) }
    }

    fun verifyBlocking(inlinedOxsts: InlinedOxsts, progress: ProgressContext): VerificationResult {
        return runBlocking { verify(inlinedOxsts, progress) }
    }

    override fun close()

    class Builder internal constructor() {
        private var injector: Injector? = null
        private var context: SemantifyrModelContext? = null
        private var verificationPortfolio: VerificationPortfolio? = null
        private var artifacts: ArtifactConfig? = null
        private var environment: ExecutionEnvironment = ExecutionEnvironment.Empty
        private var timeout: Duration = 5.minutes
        private var maxConcurrency: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        private var optimization: OptimizationConfig = OptimizationConfig.DEFAULT

        /**
         * Share an existing [Injector] (typically the one that already loaded the model) with the verifier.
         * When omitted, the builder creates a fresh OXSTS standalone injector. Reusing an injector avoids
         * concurrent Guice injector-creation races under heavy Xtext / EMF activity (e.g. in the LSP).
         */
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
                artifacts = resolvedArtifacts,
                environment = environment,
                timeout = timeout,
                maxConcurrency = maxConcurrency,
                optimization = optimization,
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
