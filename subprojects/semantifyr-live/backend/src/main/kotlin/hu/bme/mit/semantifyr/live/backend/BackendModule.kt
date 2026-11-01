/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import com.google.inject.AbstractModule
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServiceRegistry
import hu.bme.mit.semantifyr.live.backend.lsp.language.LiveLanguageServiceRegistry
import hu.bme.mit.semantifyr.live.backend.lsp.session.LiveVerificationExecutor
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationExecutor
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

class BackendModule(
    private val config: BackendConfig,
) : AbstractModule() {

    override fun configure() {
        super.configure()
        bind(BackendConfig::class.java).toInstance(config)
        bind(LanguageServiceRegistry::class.java).to(LiveLanguageServiceRegistry::class.java)
        bind(VerificationExecutor::class.java).to(LiveVerificationExecutor::class.java)
        bind(PrometheusMeterRegistry::class.java).toInstance(PrometheusMeterRegistry(PrometheusConfig.DEFAULT))
        bind(MeterRegistry::class.java).to(PrometheusMeterRegistry::class.java)
    }
}
