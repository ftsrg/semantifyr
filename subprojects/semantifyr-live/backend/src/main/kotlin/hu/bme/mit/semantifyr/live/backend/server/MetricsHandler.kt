/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import io.ktor.http.ContentType
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.Gauge
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

private const val THETA_JAR = "theta-xsts-cli.jar"
private val prometheusTextFormat = ContentType.parse("text/plain; version=0.0.4; charset=utf-8")

@Singleton
class MetricsHandler @Inject constructor(
    private val config: BackendConfig,
    private val registry: PrometheusMeterRegistry,
) {

    private val logger by loggerFactory()

    init {
        Gauge.builder("semantifyr.live.theta.processes") {
            countThetaProcesses()
        }.register(registry)
    }

    fun Application.configure() {
        val metricsPort = config.server.metricsPort
        if (metricsPort == null) {
            logger.info { "No metrics port configured, metrics are not served" }
            return
        }

        logger.info { "Serving Prometheus metrics on port $metricsPort at /metrics" }
        install(MicrometerMetrics) {
            registry = this@MetricsHandler.registry
            // the managers register meters before this plugin installs, too early for its registry-wide filter
            registerDistributionStatisticConfig = false
            filter {
                it.request.local.localPort != metricsPort
            }
        }
        routing {
            // the tunnel reaches only server.port, so the public hostname never routes here
            localPort(metricsPort) {
                get("/metrics") {
                    call.respondText(registry.scrape(), prometheusTextFormat)
                }
            }
        }
    }

    private fun countThetaProcesses(): Long {
        return ProcessHandle.current().descendants().filter {
            it.info().commandLine().orElse("").contains(THETA_JAR)
        }.count()
    }
}
