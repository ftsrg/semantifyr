/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlinx.coroutines.sync.Semaphore

@Singleton
class VerificationManager @Inject constructor(
    backendConfig: BackendConfig,
) {

    private val logger by loggerFactory()

    private val gate = Semaphore(backendConfig.verification.concurrency)

    val availablePermits: Int
        get() = gate.availablePermits

    val maxPermits = backendConfig.verification.concurrency

    suspend fun <T> withPermit(block: suspend () -> T): T {
        gate.acquire()
        logger.info { "Verification permit acquired (available=$availablePermits/$maxPermits)" }
        try {
            return block()
        } finally {
            gate.release()
            logger.info { "Verification permit released (available=$availablePermits/$maxPermits)" }
        }
    }
}
