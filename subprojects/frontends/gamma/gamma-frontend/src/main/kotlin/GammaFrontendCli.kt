/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.frontend

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import hu.bme.mit.semantifyr.frontends.gamma.frontend.commands.CompileCommand
import hu.bme.mit.semantifyr.frontends.gamma.frontend.commands.VerifyCommand

class GammaFrontendCommand : CliktCommand("gamma-frontend") {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    GammaFrontendCommand().subcommands(
        CompileCommand(),
        VerifyCommand(),
    ).main(args)
}
