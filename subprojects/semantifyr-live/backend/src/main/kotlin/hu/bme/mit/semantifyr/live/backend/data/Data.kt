/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.data

import hu.bme.mit.semantifyr.live.backend.Flavor
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class InfoResponse(
    val uptime: Duration,
    val startedAt: Instant,
    val commit: String,
    val buildTime: String,
    val activeSessions: Int,
    val maxSessions: Int,
)

@Serializable
data class FlavorResponse(
    val id: String,
    val displayName: String,
    val languageId: String,
    val fileName: String,
    val verificationCommand: String,
    val discoveryCommand: String,
    val validateWitnessCommand: String?,
    val peekCompiledOutput: Boolean,
) {
    companion object {
        fun fromFlavor(flavor: Flavor) = FlavorResponse(
            id = flavor.id,
            displayName = flavor.displayName,
            languageId = flavor.language.id,
            fileName = flavor.fileName,
            verificationCommand = flavor.verificationCommand,
            discoveryCommand = flavor.discoveryCommand,
            validateWitnessCommand = flavor.validateWitnessCommand,
            peekCompiledOutput = flavor.peekCompiledOutput,
        )
    }
}

@Serializable
data class PortfolioResponse(
    val id: String,
    val displayName: String,
    val description: String,
    val available: Boolean,
)

@Serializable
data class PortfoliosResponse(
    val portfolios: List<PortfolioResponse>,
)

@Serializable
data class FlavorsResponse(
    val flavors: List<FlavorResponse>,
)

@Serializable
data class AdminStatusResponse(
    val sessions: List<SessionInfo>,
)

@Serializable
enum class VerificationKind {
    Verify,
    Validate,
}

@Serializable
data class ActiveVerificationInfo(
    val verificationId: String,
    val portfolioId: String,
    val kind: VerificationKind,
    val elapsed: Duration = Duration.ZERO,
)

@Serializable
data class SessionInfo(
    val sessionId: String,
    val remoteIp: String,
    val flavorId: String,
    val uptime: Duration,
    val workingDirectory: String,
    val activeVerifications: List<ActiveVerificationInfo>,
    val sessionLspInfo: SessionLspInfo,
)

@Serializable
data class SessionLspInfo(
    val timeSinceLastClientMessage: Duration,
    val timeSinceLastServerMessage: Duration,
)

@Serializable
data class AdminConfigResponse(
    val development: Boolean,
    val server: AdminServerConfigResponse,
    val sessionManager: AdminSessionManagerConfigResponse,
    val verification: AdminVerificationConfigResponse,
)

@Serializable
data class AdminServerConfigResponse(
    val port: Int,
    val pingPeriod: Duration,
    val pingTimeout: Duration,
    val webRootDirectory: String?,
    val adminPasswordSet: Boolean,
    val sessionIdleTimeout: Duration,
    val wsHandshakesPerPeriod: Int,
    val wsHandshakeRatePeriod: Duration,
    val maxWsFrameSize: Long,
    val httpsOnlyCookies: Boolean,
)

@Serializable
data class AdminSessionManagerConfigResponse(
    val maxSessionsGlobal: Int,
    val semanticLibrariesDirectory: String?,
    val rootWorkDirectory: String,
)

@Serializable
data class AdminVerificationConfigResponse(
    val concurrency: Int,
    val timeout: Duration,
)
