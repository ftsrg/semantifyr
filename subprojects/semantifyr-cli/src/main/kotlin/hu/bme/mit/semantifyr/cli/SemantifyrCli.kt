/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.parse
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parsers.CommandLineParser
import com.google.inject.Inject
import hu.bme.mit.semantifyr.cli.commands.CompileCommand
import hu.bme.mit.semantifyr.cli.commands.ListCommand
import hu.bme.mit.semantifyr.cli.commands.MetricsCommand
import hu.bme.mit.semantifyr.cli.commands.PortfoliosCommand
import hu.bme.mit.semantifyr.cli.commands.VerifyCommand
import hu.bme.mit.semantifyr.cli.commands.options.applyLogLevel
import hu.bme.mit.semantifyr.cli.commands.options.logLevelOption
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup

class SemantifyrCommand @Inject constructor(
    verifyCommand: VerifyCommand,
    compileCommand: CompileCommand,
    listCommand: ListCommand,
    metricsCommand: MetricsCommand,
    portfoliosCommand: PortfoliosCommand,
) : SuspendingCliktCommand("semantifyr") {
    @Suppress("unused")
    private val logLevel by logLevelOption()

    init {
        versionOption(javaClass.`package`.implementationVersion ?: "unknown")
        subcommands(
            verifyCommand,
            compileCommand,
            listCommand,
            metricsCommand,
            portfoliosCommand,
        )
    }

    override fun help(context: Context): String {
        return "The Semantifyr command-line interface. See --help for more details."
    }

    override suspend fun run() {
        applyLogLevel(logLevel)
    }
}

suspend fun SemantifyrCommand.execute(arguments: List<String>) {
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

suspend fun main(args: Array<String>) {
    val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    val semantifyrCommand = injector.getInstance(SemantifyrCommand::class.java)
    CommandLineParser.main(semantifyrCommand) {
        semantifyrCommand.execute(args.asList())
    }
}
