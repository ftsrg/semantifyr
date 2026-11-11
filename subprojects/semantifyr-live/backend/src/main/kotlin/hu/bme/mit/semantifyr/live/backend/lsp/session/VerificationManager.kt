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
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

@Singleton
class VerificationManager @Inject constructor(
    backendConfig: BackendConfig,
    registry: MeterRegistry,
) {

    private val logger by loggerFactory()

    private val lock = Any()

    private var freePermits = backendConfig.verification.concurrency

    private var waiting = 0

    private val sessionQueues = LinkedHashMap<String, ArrayDeque<PermitRequest>>()

    private var saturatedBefore = Duration.ZERO

    private var saturatedSince: TimeSource.Monotonic.ValueTimeMark? = null

    val availablePermits: Int
        get() {
            return synchronized(lock) {
                freePermits
            }
        }

    val maxPermits = backendConfig.verification.concurrency

    val queuedCount: Int
        get() {
            return synchronized(lock) {
                waiting
            }
        }

    val runningCount: Int
        get() {
            return maxPermits - availablePermits
        }

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
        FunctionCounter.builder("semantifyr.live.verifications.saturated", this) {
            it.saturatedSeconds()
        }.baseUnit("seconds").register(registry)
    }

    private fun saturatedSeconds(): Double {
        val saturated = synchronized(lock) {
            val current = saturatedSince?.elapsedNow() ?: Duration.ZERO
            saturatedBefore + current
        }
        return saturated.toDouble(DurationUnit.SECONDS)
    }

    private fun takePermit() {
        freePermits--
        if (freePermits == 0) {
            saturatedSince = TimeSource.Monotonic.markNow()
        }
    }

    private fun returnPermit() {
        saturatedSince?.let {
            saturatedBefore += it.elapsedNow()
        }
        saturatedSince = null
        freePermits++
    }

    fun enqueue(sessionId: String): PermitRequest {
        val request = PermitRequest(sessionId)
        synchronized(lock) {
            if (freePermits > 0 && sessionQueues.isEmpty()) {
                takePermit()
                grant(request)
            } else {
                sessionQueues.getOrPut(sessionId) {
                    ArrayDeque()
                }.addLast(request)
                waiting++
            }
        }
        return request
    }

    suspend fun <T> withPermit(request: PermitRequest, block: suspend () -> T): T {
        try {
            request.granted.await()
        } catch (e: CancellationException) {
            abandon(request)
            throw e
        }
        synchronized(lock) {
            if (request.state != PermitState.Granted) {
                throw CancellationException("Verification permit request abandoned")
            }
            request.state = PermitState.Held
        }
        logger.info { "Verification permit acquired (available=$availablePermits/$maxPermits)" }
        try {
            return block()
        } finally {
            synchronized(lock) {
                request.state = PermitState.Finished
                releasePermit()
            }
            logger.info { "Verification permit released (available=$availablePermits/$maxPermits)" }
        }
    }

    fun abandon(request: PermitRequest) {
        synchronized(lock) {
            when (request.state) {
                PermitState.Queued -> {
                    removeQueued(request)
                    request.state = PermitState.Finished
                    request.granted.cancel()
                }
                PermitState.Granted -> {
                    request.state = PermitState.Finished
                    releasePermit()
                }
                PermitState.Held,
                PermitState.Finished,
                -> {
                }
            }
        }
    }

    private fun removeQueued(request: PermitRequest) {
        val queue = sessionQueues[request.sessionId] ?: return
        if (queue.remove(request)) {
            waiting--
        }
        if (queue.isEmpty()) {
            sessionQueues.remove(request.sessionId)
        }
    }

    private fun releasePermit() {
        val longestWaiting = sessionQueues.entries.firstOrNull()
        if (longestWaiting == null) {
            returnPermit()
            return
        }
        val sessionId = longestWaiting.key
        val queue = longestWaiting.value
        sessionQueues.remove(sessionId)
        val next = queue.removeFirst()
        waiting--
        if (queue.isNotEmpty()) {
            sessionQueues[sessionId] = queue
        }
        grant(next)
    }

    private fun grant(request: PermitRequest) {
        request.state = PermitState.Granted
        request.granted.complete(Unit)
    }
}

internal enum class PermitState {
    Queued,
    Granted,
    Held,
    Finished,
}

class PermitRequest(
    val sessionId: String,
) {
    internal val granted = CompletableDeferred<Unit>()

    internal var state = PermitState.Queued
}
