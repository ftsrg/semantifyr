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
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionContext
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScope
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScoped
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationExecutor
import hu.bme.mit.semantifyr.live.backend.lsp.session.seededKeyProvider

class BackendModule(
    private val config: BackendConfig,
) : AbstractModule() {

    override fun configure() {
        super.configure()
        bind(BackendConfig::class.java).toInstance(config)
        bind(LanguageServiceRegistry::class.java).to(LiveLanguageServiceRegistry::class.java)
        bind(VerificationExecutor::class.java).to(LiveVerificationExecutor::class.java)

        bindScope(SessionScoped::class.java, SessionScope)
        bind(SessionContext::class.java)
            .toProvider(seededKeyProvider())
            .`in`(SessionScoped::class.java)
    }
}
