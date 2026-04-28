/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.google.inject.Guice
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.BackendConfigValidator
import hu.bme.mit.semantifyr.live.backend.BackendModule
import hu.bme.mit.semantifyr.live.backend.server.Server
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import java.nio.file.Path

class StartCommand : CliktCommand("start") {
    private val logger by loggerFactory()

    private val configFile: Path? by option("--config", "-c", help = "Path to the config file. If not specified, then config is read from the environment variables.")
        .path(mustExist = true, canBeDir = false, mustBeReadable = true)

    override fun run() {
        val config = configFile?.let {
            BackendConfig.fromFile(it)
        } ?: BackendConfig.fromEnvironment()

        logger.info { "Starting Semantifyr Live backend with config: $config" }

        BackendConfigValidator.validate(config)

        val injector = Guice.createInjector(BackendModule(config))
        val server = injector.getInstance(Server::class.java)

        Runtime.getRuntime().addShutdownHook(
            Thread {
                logger.info { "Shutdown hook fired; closing server" }
                server.close()
            },
        )

        server.start()
    }
}
