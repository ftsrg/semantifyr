/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.session.SessionManager
import io.ktor.http.ContentType
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.Gauge
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

@Singleton
class MetricsHandler @Inject constructor(
    private val registry: PrometheusMeterRegistry,
    private val sessionManager: SessionManager,
) {

    fun Application.configure() {
        Gauge.builder("semantifyr.sessions.active") { sessionManager.activeSessions }
            .description("Number of active LSP sessions")
            .register(registry)
        Gauge.builder("semantifyr.sessions.max") { sessionManager.maxSessions }
            .description("Global maximum number of LSP sessions")
            .register(registry)

        install(MicrometerMetrics) {
            this.registry = this@MetricsHandler.registry
        }
        routing {
            get("/api/metrics") {
                call.respondText(registry.scrape(), ContentType.parse("text/plain; version=0.0.4"))
            }
        }
    }
}
