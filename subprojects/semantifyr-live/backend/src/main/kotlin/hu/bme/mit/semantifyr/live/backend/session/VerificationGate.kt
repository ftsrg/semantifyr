/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.FlavorRegistry
import hu.bme.mit.semantifyr.live.backend.utils.debug
import hu.bme.mit.semantifyr.live.backend.utils.info
import hu.bme.mit.semantifyr.live.backend.utils.loggerFactory
import hu.bme.mit.semantifyr.live.backend.utils.warn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage

class VerificationLimitReachedException(message: String) : RuntimeException(message)

@Singleton
class VerificationGate {

    private val logger by loggerFactory()

    @Inject
    private lateinit var config: BackendConfig

    private val gate: Semaphore by lazy {
        Semaphore(config.verification.concurrency)
    }

    val verificationCommands: Set<String> by lazy {
        FlavorRegistry.flavors.mapNotNull { it.verifyCommand }.toSet()
    }

    val availablePermits: Int
        get() = gate.availablePermits

    /**
     * Register a verification. Acquires a permit, starts a timeout coroutine
     * in [scope], and returns a [Job] representing the verification.
     *
     * When the job completes (timeout, cancellation, or explicit cancel),
     * the permit is released automatically.
     *
     * On timeout, [onTimeout] is called before the job completes.
     *
     * @throws VerificationLimitReachedException if no permit is available.
     */
    fun registerVerification(scope: CoroutineScope, onTimeout: suspend () -> Unit): Job {
        if (!gate.tryAcquire()) {
            logger.warn { "Verification permit denied (available=0, max=${config.verification.concurrency})" }
            throw VerificationLimitReachedException("Verification limit reached, please try again later.")
        }

        logger.debug { "Verification permit acquired (available=$availablePermits/${config.verification.concurrency})" }

        val job = scope.launch {
            delay(config.verification.timeout)
            logger.info { "Verification timeout elapsed (${config.verification.timeout})" }
            onTimeout()
        }

        job.invokeOnCompletion {
            gate.release()
            logger.info { "Verification permit released (available=$availablePermits/${config.verification.concurrency})" }
        }

        return job
    }

    fun isVerificationRequest(message: RequestMessage): Boolean {
        val params = message.params as? ExecuteCommandParams ?: return false
        return params.command in verificationCommands
    }
}
