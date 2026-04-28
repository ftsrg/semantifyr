/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import java.nio.file.Files

class InvalidConfigurationException(message: String) : RuntimeException(message)

/**
 * Validates that a [BackendConfig] can actually be used to run the server. Catches
 * misconfiguration (missing/unreadable LSP binaries, unwritable workspace, conflicting timeouts)
 * before any client connects, so failures surface at startup rather than on the first request.
 */
object BackendConfigValidator {

    fun validate(
        config: BackendConfig,
        flavors: List<Flavor> = FlavorRegistry.flavors,
    ) {
        val problems = mutableListOf<String>()

        validateRootWorkDirectory(config, problems)
        validateLspBinaries(config, flavors, problems)
        validateTimeouts(config, problems)
        validateLimits(config, problems)

        if (problems.isNotEmpty()) {
            throw InvalidConfigurationException(
                "Backend configuration is invalid:\n  - " + problems.joinToString("\n  - "),
            )
        }
    }

    private fun validateRootWorkDirectory(config: BackendConfig, problems: MutableList<String>) {
        val rootWork = config.sessionManager.rootWorkPath
        try {
            Files.createDirectories(rootWork)
        } catch (e: Exception) {
            problems += "Cannot create rootWorkDirectory '$rootWork': ${e.message}"
            return
        }
        if (!Files.isWritable(rootWork)) {
            problems += "rootWorkDirectory '$rootWork' is not writable"
        }
    }

    private fun validateLspBinaries(
        config: BackendConfig,
        flavors: List<Flavor>,
        problems: MutableList<String>,
    ) {
        val binariesPath = config.sessionManager.lspBinariesPath
        if (binariesPath == null) {
            problems += "lspBinariesDirectory is not set (env: SEMANTIFYR_LIVE_LSP_BINARIES_DIR)"
            return
        }
        if (!Files.isDirectory(binariesPath)) {
            problems += "lspBinariesDirectory '$binariesPath' does not exist or is not a directory"
            return
        }
        for (flavor in flavors) {
            val binary = binariesPath.resolve(flavor.binaryRelativePath)
            if (!Files.isExecutable(binary)) {
                problems += "Flavor '${flavor.id}' binary '$binary' is missing or not executable"
            }
        }
    }

    private fun validateTimeouts(config: BackendConfig, problems: MutableList<String>) {
        if (config.server.sessionIdleTimeout <= config.verification.timeout) {
            problems += "server.sessionIdleTimeout (${config.server.sessionIdleTimeout}) must be greater than " +
                "verification.timeout (${config.verification.timeout}) so long verifications aren't evicted"
        }
    }

    private fun validateLimits(config: BackendConfig, problems: MutableList<String>) {
        if (config.verification.concurrency < 1) {
            problems += "verification.concurrency must be >= 1 (got ${config.verification.concurrency})"
        }
        if (config.sessionManager.maxSessionsGlobal < 1) {
            problems += "sessionManager.maxSessionsGlobal must be >= 1 (got ${config.sessionManager.maxSessionsGlobal})"
        }
        if (config.sessionManager.maxSessionsPerIp < 1) {
            problems += "sessionManager.maxSessionsPerIp must be >= 1 (got ${config.sessionManager.maxSessionsPerIp})"
        }
        if (config.server.wsHandshakesPerPeriod < 1) {
            problems += "server.wsHandshakesPerPeriod must be >= 1 (got ${config.server.wsHandshakesPerPeriod})"
        }
        if (!config.server.wsHandshakeRatePeriod.isPositive()) {
            problems += "server.wsHandshakeRatePeriod must be positive (got ${config.server.wsHandshakeRatePeriod})"
        }
    }
}
