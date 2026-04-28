/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands.options

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice

const val LOG_LEVEL_SYSTEM_PROPERTY = "semantifyr.log.level"

fun SuspendingCliktCommand.logLevelOption() = option("--log-level")
    .choice("ERROR", "WARN", "INFO", "DEBUG", "TRACE", ignoreCase = true)
    .default("WARN")
    .help("Root log level. Logs are written to stderr so stdout remains usable for piping. Default: WARN.")

fun applyLogLevel(logLevel: String) {
    System.setProperty(LOG_LEVEL_SYSTEM_PROPERTY, logLevel.uppercase())
}
