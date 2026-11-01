/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Injector
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.testing.handler
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.engine.connector
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val APPLICATION_PORT = 8080
private const val METRICS_PORT = 8081

class MetricsHandlerTest {

    private fun injector(metricsPort: Int? = METRICS_PORT): Injector {
        return testInjector(BackendConfig(server = ServerConfig(port = APPLICATION_PORT, metricsPort = metricsPort)))
    }

    private fun ApplicationTestBuilder.installWithConnectors(injector: Injector) {
        engine {
            connector {
                host = "localhost"
                port = APPLICATION_PORT
            }
            connector {
                host = "localhost"
                port = METRICS_PORT
            }
        }
        installSemantifyrApp {
            with(injector.handler<MetricsHandler>()) {
                configure()
            }
            with(injector.handler<ApiRoutesHandler>()) {
                configure()
            }
        }
    }

    @Test
    fun `the metrics port serves the Prometheus text format with the JVM and live meters`() = testApplication {
        installWithConnectors(injector())

        val response = client.get("http://localhost:$METRICS_PORT/metrics")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.contentType()?.withoutParameters()).isEqualTo(ContentType.Text.Plain)
        assertThat(response.bodyAsText()).contains(
            "jvm_memory_used_bytes",
            "jvm_threads_live_threads",
            "process_cpu_usage",
            "semantifyr_live_sessions_active 0",
            "semantifyr_live_sessions_limit 256",
            "semantifyr_live_verifications_queued 0",
            "semantifyr_live_verifications_running 0",
            "semantifyr_live_verifications_limit 4",
            "semantifyr_live_theta_processes",
        )
    }

    @Test
    fun `the application port does not serve metrics`() = testApplication {
        installWithConnectors(injector())

        val response = client.get("http://localhost:$APPLICATION_PORT/metrics")

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `without a metrics port nothing is served`() = testApplication {
        installWithConnectors(injector(metricsPort = null))

        for (port in listOf(APPLICATION_PORT, METRICS_PORT)) {
            val response = client.get("http://localhost:$port/metrics")
            assertThat(response.status).`as`("port $port").isEqualTo(HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `requests on the application port are timed by route and scrapes are not`() = testApplication {
        installWithConnectors(injector())

        repeat(2) {
            client.get("http://localhost:$APPLICATION_PORT/api/health")
        }
        client.get("http://localhost:$METRICS_PORT/metrics")
        val body = client.get("http://localhost:$METRICS_PORT/metrics").bodyAsText()

        val healthCount = body.lines().single {
            it.startsWith("ktor_http_server_requests_seconds_count") && "route=\"/api/health\"" in it
        }
        assertThat(healthCount).contains("status=\"200\"")
        assertThat(healthCount.substringAfterLast(' ').toDouble()).isEqualTo(2.0)
        assertThat(body).doesNotContain("route=\"/metrics\"")
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun `the Theta gauge counts descendant processes running the Theta jar`(@TempDir tmp: Path) {
        val injector = injector()
        injector.handler<MetricsHandler>()
        val gauge = injector.getInstance(MeterRegistry::class.java).get("semantifyr.live.theta.processes").gauge()
        val jar = Files.createFile(tmp.resolve("theta-xsts-cli.jar"))
        val before = gauge.value()

        val process = ProcessBuilder("sh", "-c", "sleep 60; :", jar.toString()).start()
        try {
            assertThat(gauge.value()).isEqualTo(before + 1)
        } finally {
            process.descendants().forEach {
                it.destroy()
            }
            process.destroy()
            process.waitFor(10, TimeUnit.SECONDS)
        }
        assertThat(gauge.value()).isEqualTo(before)
    }
}
