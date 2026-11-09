/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import hu.bme.mit.semantifyr.live.backend.exceptions.InvalidConfigurationException
import hu.bme.mit.semantifyr.live.backend.utils.AddressRange
import java.nio.file.Files

object BackendConfigValidator {

    fun validate(
        config: BackendConfig,
        flavors: List<Flavor> = FlavorRegistry.flavors,
    ) {
        val problems = mutableListOf<String>()

        validateRootWorkDirectory(config, problems)
        validateSemanticLibraries(config, flavors, problems)
        validateLimits(config, problems)
        validateTrustedProxies(config, problems)

        if (problems.isNotEmpty()) {
            throw InvalidConfigurationException(
                "Backend configuration is invalid:\n  - ${problems.joinToString("\n  - ")}",
            )
        }
    }

    private fun validateTrustedProxies(config: BackendConfig, problems: MutableList<String>) {
        for (range in config.server.trustedProxies) {
            try {
                AddressRange.parse(range)
            } catch (e: IllegalArgumentException) {
                problems += "server.trustedProxies entry '$range' is not an address or CIDR range: ${e.message}"
            }
        }
        if (config.server.clientAddressHeader != null && config.server.trustedProxies.isEmpty()) {
            problems += "server.trustedProxies must be set when server.clientAddressHeader is (env: SEMANTIFYR_LIVE_TRUSTED_PROXIES)"
        }
        if (config.server.userHeader != null && config.server.trustedProxies.isEmpty()) {
            problems += "server.trustedProxies must be set when server.userHeader is (env: SEMANTIFYR_LIVE_TRUSTED_PROXIES)"
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

    private fun validateLimits(config: BackendConfig, problems: MutableList<String>) {
        if (config.verification.concurrency < 1) {
            problems += "verification.concurrency must be >= 1 (got ${config.verification.concurrency})"
        }
        if (config.verification.maxPerSession < 1) {
            problems += "verification.maxPerSession must be >= 1 (got ${config.verification.maxPerSession})"
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
        val metricsPort = config.server.metricsPort
        if (metricsPort != null && metricsPort !in 1..65535) {
            problems += "server.metricsPort must be between 1 and 65535 (got $metricsPort)"
        }
        if (metricsPort == config.server.port) {
            problems += "server.metricsPort must differ from server.port (got $metricsPort)"
        }
    }
}
