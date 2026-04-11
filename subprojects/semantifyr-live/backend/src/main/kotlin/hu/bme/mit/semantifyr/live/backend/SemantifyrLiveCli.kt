/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import hu.bme.mit.semantifyr.live.backend.commands.StartCommand

class SemantifyrLiveCommand : CliktCommand("semantifyr-live") {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    SemantifyrLiveCommand().subcommands(
        StartCommand(),
    ).main(args)
}
