/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.language

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings
import hu.bme.mit.semantifyr.lang.ide.server.concurrent.LockingRequestManager
import hu.bme.mit.semantifyr.lang.ide.server.wire.ArtifactsLocation
import hu.bme.mit.semantifyr.lang.ide.server.wire.ServerSettingsPayload
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionRequestManager
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScoped

class LiveLanguageServerModule : AbstractModule() {

    override fun configure() {
        super.configure()
        bind(LockingRequestManager::class.java)
            .to(SessionRequestManager::class.java)
            .`in`(SessionScoped::class.java)
    }

    @Provides
    @Singleton
    fun provideServerSettings(): ServerSettings {
        return ServerSettings().apply {
            apply(ServerSettingsPayload.withArtifactsLocation(ArtifactsLocation.WORKSPACE))
        }
    }
}
