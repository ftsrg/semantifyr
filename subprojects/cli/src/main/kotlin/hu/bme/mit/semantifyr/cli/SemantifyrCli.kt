/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import com.github.ajalt.clikt.parsers.CommandLineParser
import com.google.inject.Inject
import hu.bme.mit.semantifyr.cli.commands.CompileCommand
import hu.bme.mit.semantifyr.cli.commands.ListCommand
import hu.bme.mit.semantifyr.cli.commands.MetricsCommand
import hu.bme.mit.semantifyr.cli.commands.PortfoliosCommand
import hu.bme.mit.semantifyr.cli.commands.VerifyCommand
import hu.bme.mit.semantifyr.cli.common.RootCommand
import hu.bme.mit.semantifyr.cli.common.execute
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup

class SemantifyrCommand @Inject constructor(
    verifyCommand: VerifyCommand,
    compileCommand: CompileCommand,
    listCommand: ListCommand,
    metricsCommand: MetricsCommand,
    portfoliosCommand: PortfoliosCommand,
) : RootCommand(
    "semantifyr",
    "The Semantifyr command-line interface for OXSTS models. See --help for more details.",
    listOf(
        verifyCommand,
        compileCommand,
        listCommand,
        metricsCommand,
        portfoliosCommand,
    ),
)

suspend fun main(args: Array<String>) {
    val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    val semantifyrCommand = injector.getInstance(SemantifyrCommand::class.java)
    CommandLineParser.main(semantifyrCommand) {
        semantifyrCommand.execute(args.asList())
    }
}
