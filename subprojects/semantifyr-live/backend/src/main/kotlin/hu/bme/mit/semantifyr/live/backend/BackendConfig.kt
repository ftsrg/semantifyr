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
    val cors: CorsConfig = CorsConfig(),
    val webRootDirectory: String? = null,
) {
    fun withEnv(env: Map<String, String?>) = copy(
        port = env["SEMANTIFYR_LIVE_PORT"]?.toIntOrNull() ?: port,
        pingPeriod = env["SEMANTIFYR_LIVE_PING_PERIOD_SECONDS"]?.toLongOrNull()?.seconds ?: pingPeriod,
        cors = cors.withEnv(env),
        webRootDirectory = env["SEMANTIFYR_LIVE_WEB_ROOT_DIR"] ?: webRootDirectory,
    )

    val webRootPath by lazy {
        webRootDirectory?.let(Path::of)?.takeIf {
            Files.isDirectory(it)
        }
    }
}

@Serializable
data class SessionManagerConfig(
    val maxSessionsGlobal: Int = 32,
    val maxSessionsPerIp: Int = 4,
    val lspBinariesDirectory: String? = null,
    val rootWorkDirectory: String = "/var/lib/semantifyr-live",
) {
    fun withEnv(env: Map<String, String?>) = copy(
        maxSessionsGlobal = env["SEMANTIFYR_LIVE_MAX_SESSIONS_GLOBAL"]?.toIntOrNull() ?: maxSessionsGlobal,
        maxSessionsPerIp = env["SEMANTIFYR_LIVE_MAX_SESSIONS_PER_IP"]?.toIntOrNull() ?: maxSessionsPerIp,
        lspBinariesDirectory = env["SEMANTIFYR_LIVE_LSP_BINARIES_DIR"] ?: lspBinariesDirectory,
        rootWorkDirectory = env["SEMANTIFYR_LIVE_ROOT_WORK_DIR"] ?: rootWorkDirectory,
    )

    val lspBinariesPath by lazy {
        Path.of(lspBinariesDirectory)
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

@Serializable
data class CorsConfig(
    val allowedOrigins: Set<String> = setOf("ftsrg.mit.bme.hu"),
) {
    fun withEnv(env: Map<String, String?>): CorsConfig {
        val raw = env["SEMANTIFYR_LIVE_ALLOWED_ORIGINS"] ?: return this
        return copy(
            allowedOrigins = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        )
    }
}
