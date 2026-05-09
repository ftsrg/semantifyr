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
import kotlin.getValue
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
    val pingPeriod: Duration = 30.seconds,
    val pingTimeout: Duration = 15.seconds,
    val webRootDirectory: String? = null,
    val adminPassword: String? = null,
    val sessionIdleTimeout: Duration = 10.minutes,
    val wsHandshakesPerPeriod: Int = 10,
    val wsHandshakeRatePeriod: Duration = 1.minutes,
    val maxWsFrameSize: Long = 4 * 1024 * 1024,
    val httpsOnlyCookies: Boolean = true,
) {
    fun withEnv(env: Map<String, String?>) = copy(
        port = env["SEMANTIFYR_LIVE_PORT"]?.toIntOrNull() ?: port,
        pingPeriod = env["SEMANTIFYR_LIVE_PING_PERIOD_SECONDS"]?.toLongOrNull()?.seconds ?: pingPeriod,
        webRootDirectory = env["SEMANTIFYR_LIVE_WEB_ROOT_DIR"] ?: webRootDirectory,
        adminPassword = env["SEMANTIFYR_LIVE_ADMIN_PASSWORD"] ?: adminPassword,
        sessionIdleTimeout = env["SEMANTIFYR_LIVE_SESSION_IDLE_TIMEOUT_SECONDS"]?.toLongOrNull()?.seconds ?: sessionIdleTimeout,
        wsHandshakesPerPeriod = env["SEMANTIFYR_LIVE_WS_HANDSHAKES_PER_PERIOD"]?.toIntOrNull() ?: wsHandshakesPerPeriod,
        wsHandshakeRatePeriod = env["SEMANTIFYR_LIVE_WS_HANDSHAKE_RATE_PERIOD_SECONDS"]?.toLongOrNull()?.seconds ?: wsHandshakeRatePeriod,
        maxWsFrameSize = env["SEMANTIFYR_LIVE_MAX_WS_FRAME_SIZE"]?.toLongOrNull() ?: maxWsFrameSize,
        httpsOnlyCookies = env["SEMANTIFYR_LIVE_HTTPS_ONLY_COOKIES"]?.toBooleanStrictOrNull() ?: httpsOnlyCookies,
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
    val maxSessionsGlobal: Int = 32,
    val maxSessionsPerIp: Int = 4,
    val maxConcurrentLspStarts: Int = 4,
    val lspStartCooldownMillis: Long = 0,
    val lspBinariesDirectory: String? = null,
    val semanticLibrariesDirectory: String? = null,
    val rootWorkDirectory: String = "/var/lib/semantifyr-live",
    val lspJvmOpts: String = "-Xmx256m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=128m -Xss512k",
) {
    fun withEnv(env: Map<String, String?>) = copy(
        maxSessionsGlobal = env["SEMANTIFYR_LIVE_MAX_SESSIONS_GLOBAL"]?.toIntOrNull() ?: maxSessionsGlobal,
        maxSessionsPerIp = env["SEMANTIFYR_LIVE_MAX_SESSIONS_PER_IP"]?.toIntOrNull() ?: maxSessionsPerIp,
        maxConcurrentLspStarts = env["SEMANTIFYR_LIVE_MAX_CONCURRENT_LSP_STARTS"]?.toIntOrNull() ?: maxConcurrentLspStarts,
        lspStartCooldownMillis = env["SEMANTIFYR_LIVE_LSP_START_COOLDOWN_MILLIS"]?.toLongOrNull() ?: lspStartCooldownMillis,
        lspBinariesDirectory = env["SEMANTIFYR_LIVE_LSP_BINARIES_DIR"] ?: lspBinariesDirectory,
        semanticLibrariesDirectory = env["SEMANTIFYR_LIVE_SEMANTIC_LIBRARIES_DIR"] ?: semanticLibrariesDirectory,
        rootWorkDirectory = env["SEMANTIFYR_LIVE_ROOT_WORK_DIR"] ?: rootWorkDirectory,
        lspJvmOpts = env["SEMANTIFYR_LIVE_LSP_JVM_OPTS"] ?: lspJvmOpts,
    )

    val lspBinariesPath by lazy {
        lspBinariesDirectory?.let {
            Path.of(it)
        }
    }

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
) {
    fun withEnv(env: Map<String, String?>) = copy(
        concurrency = env["SEMANTIFYR_LIVE_VERIFY_CONCURRENCY"]?.toIntOrNull() ?: concurrency,
        timeout = env["SEMANTIFYR_LIVE_VERIFY_TIMEOUT_SECONDS"]?.toLongOrNull()?.seconds ?: timeout,
    )
}
