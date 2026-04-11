/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BackendConfigTest {

    @Test
    fun `defaults are applied for every setting`() {
        val cfg = BackendConfig()
        assertThat(cfg.server.port).isEqualTo(8080)
        assertThat(cfg.server.pingPeriod).isEqualTo(30.seconds)
        assertThat(cfg.server.pingTimeout).isEqualTo(15.seconds)
        assertThat(cfg.server.cors.allowedOrigins).containsExactly("ftsrg.mit.bme.hu")
        assertThat(cfg.server.webRootDirectory).isNull()
        assertThat(cfg.sessionManager.maxSessionsGlobal).isEqualTo(32)
        assertThat(cfg.sessionManager.maxSessionsPerIp).isEqualTo(4)
        assertThat(cfg.sessionManager.lspBinariesDirectory).isNull()
        assertThat(cfg.sessionManager.rootWorkDirectory).isEqualTo("/var/lib/semantifyr-live")
        assertThat(cfg.verification.concurrency).isEqualTo(4)
        assertThat(cfg.verification.timeout).isEqualTo(5.minutes)
    }

    @Test
    fun `env vars override defaults`() {
        val cfg = BackendConfig().withEnv(
            mapOf(
                "SEMANTIFYR_LIVE_PORT" to "9090",
                "SEMANTIFYR_LIVE_PING_PERIOD_SECONDS" to "60",
                "SEMANTIFYR_LIVE_ALLOWED_ORIGINS" to "example.org, other.test",
                "SEMANTIFYR_LIVE_MAX_SESSIONS_GLOBAL" to "64",
                "SEMANTIFYR_LIVE_MAX_SESSIONS_PER_IP" to "8",
                "SEMANTIFYR_LIVE_LSP_BINARIES_DIR" to "/opt/lsp",
                "SEMANTIFYR_LIVE_ROOT_WORK_DIR" to "/tmp/sessions",
                "SEMANTIFYR_LIVE_VERIFY_CONCURRENCY" to "2",
                "SEMANTIFYR_LIVE_VERIFY_TIMEOUT_SECONDS" to "180",
            ),
        )
        assertThat(cfg.server.port).isEqualTo(9090)
        assertThat(cfg.server.pingPeriod).isEqualTo(60.seconds)
        assertThat(cfg.server.cors.allowedOrigins).containsExactlyInAnyOrder("example.org", "other.test")
        assertThat(cfg.sessionManager.maxSessionsGlobal).isEqualTo(64)
        assertThat(cfg.sessionManager.maxSessionsPerIp).isEqualTo(8)
        assertThat(cfg.sessionManager.lspBinariesDirectory).isEqualTo("/opt/lsp")
        assertThat(cfg.sessionManager.rootWorkDirectory).isEqualTo("/tmp/sessions")
        assertThat(cfg.verification.concurrency).isEqualTo(2)
        assertThat(cfg.verification.timeout).isEqualTo(180.seconds)
    }

    @Test
    fun `non-numeric values fall back to defaults`() {
        val cfg = BackendConfig().withEnv(
            mapOf(
                "SEMANTIFYR_LIVE_PORT" to "not-a-number",
                "SEMANTIFYR_LIVE_MAX_SESSIONS_GLOBAL" to "garbage",
            ),
        )
        assertThat(cfg.server.port).isEqualTo(8080)
        assertThat(cfg.sessionManager.maxSessionsGlobal).isEqualTo(32)
    }

    @Test
    fun `web root path is resolved when the directory exists`(@TempDir tmp: Path) {
        val webRoot = tmp.resolve("web").also(Files::createDirectory)
        val cfg = BackendConfig().withEnv(
            mapOf("SEMANTIFYR_LIVE_WEB_ROOT_DIR" to webRoot.toString()),
        )
        assertThat(cfg.server.webRootPath).isEqualTo(webRoot)
    }

    @Test
    fun `web root path is null when the directory does not exist`() {
        val cfg = BackendConfig().withEnv(
            mapOf("SEMANTIFYR_LIVE_WEB_ROOT_DIR" to "/this/path/does/not/exist"),
        )
        assertThat(cfg.server.webRootPath).isNull()
    }

    @Test
    fun `fromFile loads JSON and applies env overlay`(@TempDir tmp: Path) {
        val configFile = tmp.resolve("config.json")
        Files.writeString(
            configFile,
            """{"server": {"port": 3000}, "verification": {"concurrency": 1}}""",
        )
        val cfg = BackendConfig.fromFile(configFile)
        assertThat(cfg.server.port).isEqualTo(3000)
        assertThat(cfg.verification.concurrency).isEqualTo(1)
    }
}
