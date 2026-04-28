/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlinx.coroutines.sync.Semaphore

@Singleton
class GlobalVerificationManager @Inject constructor(
    config: BackendConfig,
) {

    private val logger by loggerFactory()

    private val gate = Semaphore(config.verification.concurrency)

    val availablePermits: Int
        get() = gate.availablePermits

    val maxPermits = config.verification.concurrency

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
