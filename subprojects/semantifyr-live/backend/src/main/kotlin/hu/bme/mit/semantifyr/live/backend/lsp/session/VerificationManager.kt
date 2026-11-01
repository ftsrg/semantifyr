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
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicInteger

@Singleton
class VerificationManager @Inject constructor(
    backendConfig: BackendConfig,
    registry: MeterRegistry,
) {

    private val logger by loggerFactory()

    private val gate = Semaphore(backendConfig.verification.concurrency)
    private val waiting = AtomicInteger()

    val availablePermits: Int
        get() = gate.availablePermits

    val maxPermits = backendConfig.verification.concurrency

    val queuedCount: Int
        get() = waiting.get()

    val runningCount: Int
        get() = maxPermits - availablePermits

    init {
        Gauge.builder("semantifyr.live.verifications.queued") {
            queuedCount
        }.register(registry)
        Gauge.builder("semantifyr.live.verifications.running") {
            runningCount
        }.register(registry)
        Gauge.builder("semantifyr.live.verifications.limit") {
            maxPermits
        }.register(registry)
    }

    suspend fun <T> withPermit(block: suspend () -> T): T {
        waiting.incrementAndGet()
        try {
            gate.acquire()
        } finally {
            waiting.decrementAndGet()
        }
        logger.info { "Verification permit acquired (available=$availablePermits/$maxPermits)" }
        try {
            return block()
        } finally {
            gate.release()
            logger.info { "Verification permit released (available=$availablePermits/$maxPermits)" }
        }
    }
}
