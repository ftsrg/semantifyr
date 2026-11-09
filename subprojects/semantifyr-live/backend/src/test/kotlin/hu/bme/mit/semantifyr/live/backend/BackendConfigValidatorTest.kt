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

class BackendConfigValidatorTest {

    private fun baseConfig(tmp: Path) = BackendConfig(
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
    fun `a fully specified config passes`(@TempDir tmp: Path) {
        assertThatCode {
            BackendConfigValidator.validate(baseConfig(tmp), flavors = emptyList())
        }.doesNotThrowAnyException()
    }

    @Test
    fun `a client address header without trusted proxies is rejected`(@TempDir tmp: Path) {
        val config = baseConfig(tmp).run {
            copy(server = server.copy(clientAddressHeader = "X-Forwarded-For"))
        }
        assertThatThrownBy {
            BackendConfigValidator.validate(config, flavors = emptyList())
        }.isInstanceOf(InvalidConfigurationException::class.java)
            .hasMessageContaining("trustedProxies")
    }

    @Test
    fun `a user header without trusted proxies is rejected`(@TempDir tmp: Path) {
        val config = baseConfig(tmp).run {
            copy(server = server.copy(userHeader = "X-Auth-Request-User"))
        }
        assertThatThrownBy {
            BackendConfigValidator.validate(config, flavors = emptyList())
        }.isInstanceOf(InvalidConfigurationException::class.java)
            .hasMessageContaining("server.userHeader")
    }

    @Test
    fun `the proxy settings of the deployment pass`(@TempDir tmp: Path) {
        val config = baseConfig(tmp).run {
            copy(server = server.copy(clientAddressHeader = "X-Forwarded-For", userHeader = "X-Auth-Request-User", trustedProxies = listOf("172.30.0.254/32")))
        }
        assertThatCode {
            BackendConfigValidator.validate(config, flavors = emptyList())
        }.doesNotThrowAnyException()
    }

    @Test
    fun `a malformed trusted proxy is rejected`(@TempDir tmp: Path) {
        val config = baseConfig(tmp).run {
            copy(server = server.copy(clientAddressHeader = "X-Forwarded-For", trustedProxies = listOf("caddy")))
        }
        assertThatThrownBy {
            BackendConfigValidator.validate(config, flavors = emptyList())
        }.isInstanceOf(InvalidConfigurationException::class.java)
            .hasMessageContaining("caddy")
    }

    @Test
    fun `a metrics port equal to the application port or out of range is rejected`(@TempDir tmp: Path) {
        for (metricsPort in listOf(8080, 0, 70000)) {
            val config = baseConfig(tmp).run {
                copy(server = server.copy(port = 8080, metricsPort = metricsPort))
            }
            assertThatThrownBy {
                BackendConfigValidator.validate(config, flavors = emptyList())
            }.`as`("metricsPort $metricsPort")
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("metricsPort")
        }
    }

    @Test
    fun `non-positive limits are rejected`(@TempDir tmp: Path) {
        val config = baseConfig(tmp).copy(
            verification = VerificationConfig(concurrency = 0, maxPerSession = 0),
            sessionManager = SessionManagerConfig(rootWorkDirectory = tmp.toString(), maxSessionsGlobal = 0),
        )
        assertThatThrownBy {
            BackendConfigValidator.validate(config, flavors = emptyList())
        }.isInstanceOf(InvalidConfigurationException::class.java)
            .hasMessageContaining("concurrency")
            .hasMessageContaining("maxPerSession")
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
