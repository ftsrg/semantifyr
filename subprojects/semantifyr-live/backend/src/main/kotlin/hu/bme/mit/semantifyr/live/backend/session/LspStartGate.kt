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
class LspStartGate @Inject constructor(
    config: BackendConfig,
) {

    private val logger by loggerFactory()

    private val gate = Semaphore(config.sessionManager.maxConcurrentLspStarts)

    val availablePermits: Int
        get() = gate.availablePermits

    val maxPermits = config.sessionManager.maxConcurrentLspStarts

    suspend fun acquire() {
        gate.acquire()
        logger.info { "LSP start permit acquired (available=$availablePermits/$maxPermits)" }
    }

    fun release() {
        gate.release()
        logger.info { "LSP start permit released (available=$availablePermits/$maxPermits)" }
    }

}
