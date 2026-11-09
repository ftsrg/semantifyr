/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Serializable
data class BackendConfig(
    val server: ServerConfig = ServerConfig(),
    val sessionManager: SessionManagerConfig = SessionManagerConfig(),
    val verification: VerificationConfig = VerificationConfig(),
) {
    fun withEnv(env: Map<String, String?> = System.getenv()): BackendConfig = copy(
        server = server.withEnv(env),
        sessionManager = sessionManager.withEnv(env),
        verification = verification.withEnv(env),
    )

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }

        fun fromFile(configFile: Path): BackendConfig {
            val text = Files.readString(configFile)
            return json.decodeFromString<BackendConfig>(text).withEnv()
        }

        fun fromEnvironment(): BackendConfig {
            return BackendConfig().withEnv()
        }
    }
}

@Serializable
data class ServerConfig(
    val port: Int = 8080,
    val metricsPort: Int? = null,
    val pingPeriod: Duration = 30.seconds,
    val pingTimeout: Duration = 15.seconds,
    val webRootDirectory: String? = null,
    val wsHandshakesPerPeriod: Int = 120,
    val wsHandshakeRatePeriod: Duration = 1.minutes,
    val maxWsFrameSize: Long = 4 * 1024 * 1024,
    val clientAddressHeader: String? = null,
    val userHeader: String? = null,
    val trustedProxies: List<String> = emptyList(),
) {
    fun withEnv(env: Map<String, String?>) = copy(
        port = env["SEMANTIFYR_LIVE_PORT"]?.toIntOrNull() ?: port,
        metricsPort = env["SEMANTIFYR_LIVE_METRICS_PORT"]?.toIntOrNull() ?: metricsPort,
        pingPeriod = env["SEMANTIFYR_LIVE_PING_PERIOD_SECONDS"]?.toLongOrNull()?.seconds ?: pingPeriod,
        webRootDirectory = env["SEMANTIFYR_LIVE_WEB_ROOT_DIR"] ?: webRootDirectory,
        wsHandshakesPerPeriod = env["SEMANTIFYR_LIVE_WS_HANDSHAKES_PER_PERIOD"]?.toIntOrNull() ?: wsHandshakesPerPeriod,
        wsHandshakeRatePeriod = env["SEMANTIFYR_LIVE_WS_HANDSHAKE_RATE_PERIOD_SECONDS"]?.toLongOrNull()?.seconds ?: wsHandshakeRatePeriod,
        maxWsFrameSize = env["SEMANTIFYR_LIVE_MAX_WS_FRAME_SIZE"]?.toLongOrNull() ?: maxWsFrameSize,
        clientAddressHeader = env["SEMANTIFYR_LIVE_CLIENT_ADDRESS_HEADER"] ?: clientAddressHeader,
        userHeader = env["SEMANTIFYR_LIVE_USER_HEADER"] ?: userHeader,
        trustedProxies = env["SEMANTIFYR_LIVE_TRUSTED_PROXIES"]?.split(',')?.map {
            it.trim()
        }?.filter {
            it.isNotEmpty()
        } ?: trustedProxies,
    )

    val webRootPath by lazy {
        webRootDirectory?.let {
            Path.of(it)
        }?.takeIf {
            Files.isDirectory(it)
        }
    }
}

@Serializable
data class SessionManagerConfig(
    val maxSessionsGlobal: Int = 256,
    val semanticLibrariesDirectory: String? = null,
    val rootWorkDirectory: String = "/var/lib/semantifyr-live",
) {
    fun withEnv(env: Map<String, String?>) = copy(
        maxSessionsGlobal = env["SEMANTIFYR_LIVE_MAX_SESSIONS_GLOBAL"]?.toIntOrNull() ?: maxSessionsGlobal,
        semanticLibrariesDirectory = env["SEMANTIFYR_LIVE_SEMANTIC_LIBRARIES_DIR"] ?: semanticLibrariesDirectory,
        rootWorkDirectory = env["SEMANTIFYR_LIVE_ROOT_WORK_DIR"] ?: rootWorkDirectory,
    )

    val semanticLibrariesPath by lazy {
        semanticLibrariesDirectory?.let {
            Path.of(it)
        }
    }

    val rootWorkPath by lazy {
        Path.of(rootWorkDirectory)
    }
}

@Serializable
data class VerificationConfig(
    val concurrency: Int = 4,
    val timeout: Duration = 5.minutes,
    val maxPerSession: Int = 42,
) {
    fun withEnv(env: Map<String, String?>) = copy(
        concurrency = env["SEMANTIFYR_LIVE_VERIFY_CONCURRENCY"]?.toIntOrNull() ?: concurrency,
        timeout = env["SEMANTIFYR_LIVE_VERIFY_TIMEOUT_SECONDS"]?.toLongOrNull()?.seconds ?: timeout,
        maxPerSession = env["SEMANTIFYR_LIVE_VERIFY_MAX_PER_SESSION"]?.toIntOrNull() ?: maxPerSession,
    )
}
