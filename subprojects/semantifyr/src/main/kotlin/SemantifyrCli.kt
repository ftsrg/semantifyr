/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import hu.bme.mit.semantifyr.oxsts.semantifyr.commands.CompileCommand
import hu.bme.mit.semantifyr.oxsts.semantifyr.commands.VerifyCommand
import hu.bme.mit.semantifyr.oxsts.semantifyr.commands.VerifyXstsCommand

class SemantifyrCommand : CliktCommand("semantifyr") {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    SemantifyrCommand().subcommands(
        CompileCommand(),
        VerifyCommand(),
        VerifyXstsCommand(),
    ).main(args)
}
