/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import hu.bme.mit.semantifyr.live.backend.exceptions.InvalidConfigurationException
import java.nio.file.Files

object BackendConfigValidator {

    fun validate(
        config: BackendConfig,
        flavors: List<Flavor> = FlavorRegistry.flavors,
    ) {
        val problems = mutableListOf<String>()

        validateRootWorkDirectory(config, problems)
        validateSemanticLibraries(config, flavors, problems)
        validateTimeouts(config, problems)
        validateLimits(config, problems)
        if (!config.development) {
            validateProductionPosture(config, problems)
        }

        if (problems.isNotEmpty()) {
            throw InvalidConfigurationException(
                "Backend configuration is invalid:\n  - ${problems.joinToString("\n  - ")}",
            )
        }
    }

    private fun validateProductionPosture(config: BackendConfig, problems: MutableList<String>) {
        if (config.server.adminPassword.isNullOrBlank()) {
            problems += "server.adminPassword must be set in production (env: SEMANTIFYR_LIVE_ADMIN_PASSWORD), or run with development=true"
        }
        if (!config.server.httpsOnlyCookies) {
            problems += "server.httpsOnlyCookies must be true in production, or run with development=true"
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

    private fun validateSemanticLibraries(
        config: BackendConfig,
        flavors: List<Flavor>,
        problems: MutableList<String>,
    ) {
        val flavorsNeedingLibraries = flavors.filter { it.workspaceLayout is WorkspaceLayout.WithLibrary }
        if (flavorsNeedingLibraries.isEmpty()) {
            return
        }
        val librariesPath = config.sessionManager.semanticLibrariesPath
        if (librariesPath == null) {
            problems += "semanticLibrariesDirectory is not set (env: SEMANTIFYR_LIVE_SEMANTIC_LIBRARIES_DIR)"
            return
        }
        if (!Files.isDirectory(librariesPath)) {
            problems += "semanticLibrariesDirectory '$librariesPath' does not exist or is not a directory"
            return
        }
        for (flavor in flavorsNeedingLibraries) {
            val layout = flavor.workspaceLayout as WorkspaceLayout.WithLibrary
            val libraryDir = librariesPath.resolve(layout.libraryRelativePath)
            if (!Files.isDirectory(libraryDir)) {
                problems += "Flavor '${flavor.id}' library directory '$libraryDir' is missing"
            }
        }
    }

    private fun validateTimeouts(config: BackendConfig, problems: MutableList<String>) {
        if (config.server.sessionIdleTimeout <= config.verification.timeout) {
            problems += "server.sessionIdleTimeout (${config.server.sessionIdleTimeout}) must be greater than verification.timeout (${config.verification.timeout}) so long verifications are not evicted"
        }
    }

    private fun validateLimits(config: BackendConfig, problems: MutableList<String>) {
        if (config.verification.concurrency < 1) {
            problems += "verification.concurrency must be >= 1 (got ${config.verification.concurrency})"
        }
        if (config.sessionManager.maxSessionsGlobal < 1) {
            problems += "sessionManager.maxSessionsGlobal must be >= 1 (got ${config.sessionManager.maxSessionsGlobal})"
        }
        if (config.server.wsHandshakesPerPeriod < 1) {
            problems += "server.wsHandshakesPerPeriod must be >= 1 (got ${config.server.wsHandshakesPerPeriod})"
        }
        if (!config.server.wsHandshakeRatePeriod.isPositive()) {
            problems += "server.wsHandshakeRatePeriod must be positive (got ${config.server.wsHandshakeRatePeriod})"
        }
    }
}
