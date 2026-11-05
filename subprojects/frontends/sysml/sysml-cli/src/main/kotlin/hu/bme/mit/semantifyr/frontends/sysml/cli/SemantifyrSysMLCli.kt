/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.cli

import com.github.ajalt.clikt.parsers.CommandLineParser
import com.google.inject.Inject
import hu.bme.mit.semantifyr.cli.common.RootCommand
import hu.bme.mit.semantifyr.cli.common.execute
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup

internal const val SYSML_FILE_SUFFIX = ".sysml"

internal const val MODEL_HELP = "SysML v2 model file."

class SemantifyrSysMLCommand @Inject constructor(
    verifyCommand: SysMLVerifyCommand,
    compileCommand: SysMLCompileCommand,
) : RootCommand(
    "semantifyr-sysml",
    "Semantifyr for SysML v2 models.",
    listOf(
        verifyCommand,
        compileCommand,
    ),
)

suspend fun main(args: Array<String>) {
    val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    val command = injector.getInstance(SemantifyrSysMLCommand::class.java)
    CommandLineParser.main(command) {
        command.execute(args.asList())
    }
}
