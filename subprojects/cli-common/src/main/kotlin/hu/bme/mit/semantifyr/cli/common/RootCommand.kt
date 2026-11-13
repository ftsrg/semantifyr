/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.parse
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator

const val DOCUMENTATION_URL = "https://ftsrg.mit.bme.hu/semantifyr/"

abstract class RootCommand(
    name: String,
    private val description: String,
    subcommands: List<SuspendingCliktCommand>,
) : SuspendingCliktCommand(name) {
    private val logLevel by option("--log-level")
        .choice(
            "error" to Level.ERROR,
            "warn" to Level.WARN,
            "info" to Level.INFO,
            "debug" to Level.DEBUG,
            "trace" to Level.TRACE,
            ignoreCase = true,
        )
        .default(Level.WARN, defaultForHelp = "warn")
        .help("How much is logged to stderr.")

    init {
        versionOption(javaClass.`package`.implementationVersion ?: "unknown")
        subcommands(subcommands)
        context {
            helpFormatter = {
                MordantHelpFormatter(it, showDefaultValues = true)
            }
        }
    }

    override fun help(context: Context): String {
        return description
    }

    override fun helpEpilog(context: Context): String {
        return "Documentation: $DOCUMENTATION_URL"
    }

    // the logging configuration is loaded before the arguments are parsed
    override suspend fun run() {
        Configurator.setRootLevel(logLevel)
    }
}

suspend fun SuspendingCliktCommand.execute(arguments: List<String>) {
    try {
        parse(arguments)
    } catch (error: UsageError) {
        echoFormattedHelp(error)
        throw ProgramResult(ExitCode.Usage.code)
    } catch (error: PrintHelpMessage) {
        if (!error.error) {
            throw error
        }
        echo(getFormattedHelp(error), err = true)
        throw ProgramResult(ExitCode.Usage.code)
    }
}
