/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import hu.bme.mit.semantifyr.live.backend.exceptions.InvalidConfigurationException
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

class BackendConfigValidatorTest {

    private fun baseConfig(tmp: Path) = BackendConfig(
        server = ServerConfig(adminPassword = "secret", httpsOnlyCookies = true),
        sessionManager = SessionManagerConfig(rootWorkDirectory = tmp.toString()),
    )

    private fun libraryNeedingFlavor(libraryRelativePath: String) = Flavor(
        id = "needs-library",
        displayName = "Needs library",
        fileName = "snippet.oxsts",
        language = Language.Oxsts,
        workspaceLayout = WorkspaceLayout.WithLibrary(Path.of(libraryRelativePath), "Library"),
        verificationCommand = "oxsts.case.verify",
        discoveryCommand = "oxsts.case.discover",
    )

    @Test
    fun `a fully specified production config passes`(@TempDir tmp: Path) {
        assertThatCode { BackendConfigValidator.validate(baseConfig(tmp), flavors = emptyList()) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `a production config without an admin password is rejected`(@TempDir tmp: Path) {
        val config = baseConfig(tmp).run { copy(server = server.copy(adminPassword = null)) }
        assertThatThrownBy { BackendConfigValidator.validate(config, flavors = emptyList()) }
            .isInstanceOf(InvalidConfigurationException::class.java)
            .hasMessageContaining("adminPassword")
    }

    @Test
    fun `development mode skips production posture checks`(@TempDir tmp: Path) {
        val config = BackendConfig(
            development = true,
            server = ServerConfig(adminPassword = null, httpsOnlyCookies = false),
            sessionManager = SessionManagerConfig(rootWorkDirectory = tmp.toString()),
        )
        assertThatCode { BackendConfigValidator.validate(config, flavors = emptyList()) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `httpsOnlyCookies must be true in production`(@TempDir tmp: Path) {
        val config = baseConfig(tmp).run { copy(server = server.copy(httpsOnlyCookies = false)) }
        assertThatThrownBy { BackendConfigValidator.validate(config, flavors = emptyList()) }
            .isInstanceOf(InvalidConfigurationException::class.java)
            .hasMessageContaining("httpsOnlyCookies")
    }

    @Test
    fun `sessionIdleTimeout must exceed the verification timeout`(@TempDir tmp: Path) {
        val config = baseConfig(tmp).run {
            copy(server = server.copy(sessionIdleTimeout = 1.minutes), verification = VerificationConfig(timeout = 5.minutes))
        }
        assertThatThrownBy { BackendConfigValidator.validate(config, flavors = emptyList()) }
            .isInstanceOf(InvalidConfigurationException::class.java)
            .hasMessageContaining("sessionIdleTimeout")
    }

    @Test
    fun `non-positive limits are rejected`(@TempDir tmp: Path) {
        val config = baseConfig(tmp).copy(
            verification = VerificationConfig(concurrency = 0),
            sessionManager = SessionManagerConfig(rootWorkDirectory = tmp.toString(), maxSessionsGlobal = 0),
        )
        assertThatThrownBy { BackendConfigValidator.validate(config, flavors = emptyList()) }
            .isInstanceOf(InvalidConfigurationException::class.java)
            .hasMessageContaining("concurrency")
            .hasMessageContaining("maxSessionsGlobal")
    }

    @Test
    fun `flavors needing semantic libraries require the directory to be configured`(@TempDir tmp: Path) {
        assertThatThrownBy {
            BackendConfigValidator.validate(baseConfig(tmp), flavors = listOf(libraryNeedingFlavor("gamma")))
        }
            .isInstanceOf(InvalidConfigurationException::class.java)
            .hasMessageContaining("semanticLibrariesDirectory")
    }

    @Test
    fun `a missing per-flavor library directory is reported`(@TempDir tmp: Path) {
        val libraries = Files.createDirectories(tmp.resolve("libraries"))
        val config = baseConfig(tmp).copy(
            sessionManager = SessionManagerConfig(
                rootWorkDirectory = tmp.toString(),
                semanticLibrariesDirectory = libraries.toString(),
            ),
        )
        assertThatThrownBy {
            BackendConfigValidator.validate(config, flavors = listOf(libraryNeedingFlavor("absent")))
        }
            .isInstanceOf(InvalidConfigurationException::class.java)
            .hasMessageContaining("needs-library")
    }
}
