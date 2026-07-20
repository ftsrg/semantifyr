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
        val config = BackendConfig()
        assertThat(config.server.port).isEqualTo(8080)
        assertThat(config.server.pingPeriod).isEqualTo(30.seconds)
        assertThat(config.server.pingTimeout).isEqualTo(15.seconds)
        assertThat(config.server.webRootDirectory).isNull()
        assertThat(config.server.adminPassword).isNull()
        assertThat(config.sessionManager.maxSessionsGlobal).isEqualTo(256)
        assertThat(config.sessionManager.rootWorkDirectory).isEqualTo("/var/lib/semantifyr-live")
        assertThat(config.verification.concurrency).isEqualTo(4)
        assertThat(config.verification.timeout).isEqualTo(5.minutes)
    }

    @Test
    fun `env vars override defaults`() {
        val config = BackendConfig().withEnv(
            mapOf(
                "SEMANTIFYR_LIVE_PORT" to "9090",
                "SEMANTIFYR_LIVE_PING_PERIOD_SECONDS" to "60",
                "SEMANTIFYR_LIVE_MAX_SESSIONS_GLOBAL" to "64",
                "SEMANTIFYR_LIVE_ROOT_WORK_DIR" to "/tmp/sessions",
                "SEMANTIFYR_LIVE_VERIFY_CONCURRENCY" to "2",
                "SEMANTIFYR_LIVE_VERIFY_TIMEOUT_SECONDS" to "180",
                "SEMANTIFYR_LIVE_ADMIN_PASSWORD" to "secret123",
            ),
        )
        assertThat(config.server.port).isEqualTo(9090)
        assertThat(config.server.pingPeriod).isEqualTo(60.seconds)
        assertThat(config.server.adminPassword).isEqualTo("secret123")
        assertThat(config.sessionManager.maxSessionsGlobal).isEqualTo(64)
        assertThat(config.sessionManager.rootWorkDirectory).isEqualTo("/tmp/sessions")
        assertThat(config.verification.concurrency).isEqualTo(2)
        assertThat(config.verification.timeout).isEqualTo(180.seconds)
    }

    @Test
    fun `non-numeric values fall back to defaults`() {
        val config = BackendConfig().withEnv(
            mapOf(
                "SEMANTIFYR_LIVE_PORT" to "not-a-number",
                "SEMANTIFYR_LIVE_MAX_SESSIONS_GLOBAL" to "garbage",
            ),
        )
        assertThat(config.server.port).isEqualTo(8080)
        assertThat(config.sessionManager.maxSessionsGlobal).isEqualTo(256)
    }

    @Test
    fun `web root path is resolved when the directory exists`(@TempDir tmp: Path) {
        val webRoot = tmp.resolve("web").also {
            Files.createDirectory(it)
        }
        val config = BackendConfig().withEnv(
            mapOf("SEMANTIFYR_LIVE_WEB_ROOT_DIR" to webRoot.toString()),
        )
        assertThat(config.server.webRootPath).isEqualTo(webRoot)
    }

    @Test
    fun `web root path is null when the directory does not exist`() {
        val config = BackendConfig().withEnv(
            mapOf("SEMANTIFYR_LIVE_WEB_ROOT_DIR" to "/this/path/does/not/exist"),
        )
        assertThat(config.server.webRootPath).isNull()
    }

    @Test
    fun `fromFile loads JSON and applies env overlay`(@TempDir tmp: Path) {
        val configFile = tmp.resolve("config.json")
        Files.writeString(
            configFile,
            """{"server": {"port": 3000}, "verification": {"concurrency": 1}}""",
        )
        val config = BackendConfig.fromFile(configFile)
        assertThat(config.server.port).isEqualTo(3000)
        assertThat(config.verification.concurrency).isEqualTo(1)
    }
}
