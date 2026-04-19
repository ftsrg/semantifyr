/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.google.inject.Inject
import hu.bme.mit.semantifyr.cli.commands.CompileCommand
import hu.bme.mit.semantifyr.cli.commands.ListCommand
import hu.bme.mit.semantifyr.cli.commands.PortfoliosCommand
import hu.bme.mit.semantifyr.cli.commands.VerifyCommand
import hu.bme.mit.semantifyr.cli.commands.options.applyLogLevel
import hu.bme.mit.semantifyr.cli.commands.options.logLevelOption
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup

class SemantifyrCommand @Inject constructor(
    verifyCommand: VerifyCommand,
    compileCommand: CompileCommand,
    listCommand: ListCommand,
    portfoliosCommand: PortfoliosCommand,
) : SuspendingCliktCommand("semantifyr") {

    @Suppress("unused")
    private val logLevel by logLevelOption()

    init {
        subcommands(
            verifyCommand,
            compileCommand,
            listCommand,
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

suspend fun main(args: Array<String>) {
    val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    val semantifyrCommand = injector.getInstance(SemantifyrCommand::class.java)
    semantifyrCommand.main(args)
}
