/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import hu.bme.mit.semantifyr.cli.commands.CompileCommand
import hu.bme.mit.semantifyr.cli.commands.ListCommand
import hu.bme.mit.semantifyr.cli.commands.PortfoliosCommand
import hu.bme.mit.semantifyr.cli.commands.VerifyCommand
import hu.bme.mit.semantifyr.cli.commands.options.applyLogLevel
import hu.bme.mit.semantifyr.cli.commands.options.logLevelOption

class SemantifyrCommand : CliktCommand("semantifyr") {
    @Suppress("unused")
    private val logLevel by logLevelOption()

    override fun help(context: Context): String {
        return "The Semantifyr command-line interface. See --help for more details."
    }

    override fun run() {
        applyLogLevel(logLevel)
    }
}

fun main(args: Array<String>) {
    SemantifyrCommand()
        .subcommands(
            VerifyCommand(),
            CompileCommand(),
            ListCommand(),
            PortfoliosCommand(),
        )
        .main(args)
}
