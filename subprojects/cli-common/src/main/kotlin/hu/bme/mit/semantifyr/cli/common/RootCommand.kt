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
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice

abstract class RootCommand(
    name: String,
    private val description: String,
    subcommands: List<SuspendingCliktCommand>,
) : SuspendingCliktCommand(name) {
    private val logLevel by option("--log-level")
        .choice("ERROR", "WARN", "INFO", "DEBUG", "TRACE", ignoreCase = true)
        .default("WARN")
        .help("Root log level. Logs are written to stderr so stdout remains usable for piping. Default: WARN.")

    init {
        versionOption(javaClass.`package`.implementationVersion ?: "unknown")
        subcommands(subcommands)
    }

    override fun help(context: Context): String {
        return description
    }

    override suspend fun run() {
        System.setProperty("semantifyr.log.level", logLevel.uppercase())
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
