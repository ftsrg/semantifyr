/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path

object StagedTestSupport {

    val oxstsSmokeModel: Path = Path.of("../../../oxsts-test-models/smoke/smoke.semantifyr")
    val gammaSmokeDirectory: Path = Path.of("../../frontends/gamma/models/smoke")
    val sysmlSmokeDirectory: Path = Path.of("../../frontends/sysml/models/smoke")

    val semanticLibrariesDirectory = systemPropertyPath("semantifyr.live.semanticLibraries")
    val gammaCompiledModel = systemPropertyPath("semantifyr.live.gammaCompiledModel")
    val sysmlCompiledModel = systemPropertyPath("semantifyr.live.sysmlCompiledModel")

    private fun systemPropertyPath(name: String): Path? {
        return System.getProperty(name)?.let {
            Path.of(it)
        }
    }

    fun assumeStaged() {
        assumeTrue(semanticLibrariesDirectory != null, "semantifyr.live.semanticLibraries system property not set")
    }

    fun config(
        rootWorkDirectory: Path,
        maxSessionsGlobal: Int = 4,
        verification: VerificationConfig = VerificationConfig(),
    ): BackendConfig {
        val libraries = checkNotNull(semanticLibrariesDirectory) {
            "semantifyr.live.semanticLibraries must point to the staged semantic libraries directory"
        }
        return BackendConfig(
            server = ServerConfig(wsHandshakesPerPeriod = 100_000),
            verification = verification,
            sessionManager = SessionManagerConfig(
                rootWorkDirectory = rootWorkDirectory.toString(),
                semanticLibrariesDirectory = libraries.toString(),
                maxSessionsGlobal = maxSessionsGlobal,
            ),
        )
    }

    fun compiledModel(
        model: Path?,
        propertyName: String,
    ): String {
        assumeTrue(model != null, "$propertyName system property not set")
        return Files.readString(checkNotNull(model))
    }
}
