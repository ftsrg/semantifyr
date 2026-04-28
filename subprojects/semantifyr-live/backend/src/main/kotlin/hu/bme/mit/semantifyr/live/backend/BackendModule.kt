/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.name.Named
import hu.bme.mit.semantifyr.live.backend.lsp.UriRewriter
import hu.bme.mit.semantifyr.live.backend.lsp.WorkspaceSyncer
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspClientRawConnector
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspMessageInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspServerRawConnector
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionInfoMessageInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionInfoProvider
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionVerificationManager
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.VerificationMessageInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.WorkspaceSyncerInterceptor
import hu.bme.mit.semantifyr.live.backend.session.LspServerRawRunner
import hu.bme.mit.semantifyr.live.backend.session.LspSession
import hu.bme.mit.semantifyr.live.backend.session.SessionScope
import hu.bme.mit.semantifyr.live.backend.session.SessionScoped
import hu.bme.mit.semantifyr.live.backend.session.WebSocketLspClientRawConnector
import io.ktor.websocket.WebSocketSession
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.nio.file.Path

class BackendModule(
    private val config: BackendConfig,
) : AbstractModule() {

    private val sessionScope = SessionScope()

    override fun configure() {
        bind(SessionScope::class.java).toInstance(sessionScope)
        bindScope(SessionScoped::class.java, sessionScope)

        // Seeded per-session inputs: the caller installs them via SessionScope.seed() on scope entry.
        bind(Flavor::class.java).toProvider(SessionScope.seededKeyProvider<Flavor>()).`in`(SessionScoped::class.java)
        bind(WebSocketSession::class.java).toProvider(SessionScope.seededKeyProvider<WebSocketSession>()).`in`(SessionScoped::class.java)
        bind(com.google.inject.Key.get(String::class.java, com.google.inject.name.Names.named("remoteIp")))
            .toProvider(SessionScope.seededKeyProvider<String>()).`in`(SessionScoped::class.java)
        bind(com.google.inject.Key.get(String::class.java, com.google.inject.name.Names.named("sessionId")))
            .toProvider(SessionScope.seededKeyProvider<String>()).`in`(SessionScoped::class.java)

        // LspSession implements these; inject via interface so interceptors can depend on narrow contracts.
        bind(SessionInfoProvider::class.java).to(LspSession::class.java)
        bind(SessionVerificationManager::class.java).to(LspSession::class.java)

        // The runner is the production adapter to the LSP process. Interfaces let tests drop in fakes.
        bind(LspServerRawConnector::class.java).to(LspServerRawRunner::class.java)
    }

    @Provides
    @SessionScoped
    fun provideLspClientConnector(webSocketSession: WebSocketSession): LspClientRawConnector {
        return WebSocketLspClientRawConnector(webSocketSession)
    }

    @Provides
    @Singleton
    fun provideConfig(): BackendConfig = config

    @Provides
    @Singleton
    fun providePrometheusMeterRegistry(): PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    @Provides
    @SessionScoped
    @Named("workingDirectoryPath")
    fun provideWorkingDirectoryPath(config: BackendConfig, @Named("sessionId") sessionId: String): Path {
        return config.sessionManager.rootWorkPath.resolve("sessions").resolve(sessionId)
    }

    @Provides
    @SessionScoped
    @Named("clientUri")
    fun provideClientUri(): String = "file:///workspace/"

    @Provides
    @SessionScoped
    @Named("serverUri")
    fun provideServerUri(@Named("workingDirectoryPath") workingDirectoryPath: Path): String {
        val uri = workingDirectoryPath.toUri().toString()
        return if (uri.endsWith("/")) uri else "$uri/"
    }

    @Provides
    @SessionScoped
    fun provideUriRewriter(
        @Named("clientUri") clientUri: String,
        @Named("serverUri") serverUri: String,
    ): UriRewriter = UriRewriter(clientUri = clientUri, serverUri = serverUri)

    @Provides
    @SessionScoped
    fun provideWorkspaceSyncer(
        @Named("sessionId") sessionId: String,
        @Named("clientUri") clientUri: String,
        @Named("workingDirectoryPath") workingDirectoryPath: Path,
        flavor: Flavor,
    ): WorkspaceSyncer = WorkspaceSyncer(
        sessionId = sessionId,
        clientUri = clientUri,
        targetFile = workingDirectoryPath.resolve(flavor.fileName),
        verificationCommand = flavor.verificationCommand,
    )

    @Provides
    @SessionScoped
    fun provideInterceptors(
        workspaceSyncerInterceptor: WorkspaceSyncerInterceptor,
        sessionInfoMessageInterceptor: SessionInfoMessageInterceptor,
        verificationMessageInterceptor: VerificationMessageInterceptor,
    ): List<LspMessageInterceptor> = listOf(
        workspaceSyncerInterceptor,
        sessionInfoMessageInterceptor,
        verificationMessageInterceptor,
    )
}
