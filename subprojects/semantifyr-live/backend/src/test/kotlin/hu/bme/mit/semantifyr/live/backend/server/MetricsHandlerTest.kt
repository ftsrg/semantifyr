/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import hu.bme.mit.semantifyr.live.backend.session.SessionManager
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.testing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MetricsHandlerTest {

    private fun ApplicationTestBuilder.installHandler(handler: MetricsHandler) {
        install(
            createApplicationPlugin("metrics-setup") {
                with(handler) { application.configure() }
            },
        )
    }

    @Test
    fun `metrics endpoint exposes session gauges in prometheus format`() = testApplication {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val sessionManager = mock<SessionManager>()
        whenever(sessionManager.activeSessions).thenReturn(3)
        whenever(sessionManager.maxSessions).thenReturn(16)

        installHandler(MetricsHandler(registry, sessionManager))

        val response = client.get("/api/metrics")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.bodyAsText()
        assertThat(body).contains("semantifyr_sessions_active 3.0")
        assertThat(body).contains("semantifyr_sessions_max 16.0")
    }
}
