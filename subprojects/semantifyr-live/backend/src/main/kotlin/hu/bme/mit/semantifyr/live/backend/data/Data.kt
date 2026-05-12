/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.data

import hu.bme.mit.semantifyr.live.backend.Flavor
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class InfoResponse(
    val uptime: Duration,
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
    val requestId: String,
    val kind: VerificationKind = VerificationKind.Verify,
    val caseLabel: String? = null,
    val portfolioId: String? = null,
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
    val maxSessionsGlobal: Int,
    val verificationConcurrency: Int,
    val verificationTimeout: Duration,
)
