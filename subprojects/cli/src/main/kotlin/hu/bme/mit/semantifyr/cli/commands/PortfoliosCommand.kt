/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.portfolios.Portfolios

class PortfoliosCommand : SuspendingCliktCommand("portfolios") {
    private val logger by loggerFactory()

    override fun help(context: Context): String {
        return "List the backend portfolios including their legs, each leg's soundness direction and availability."
    }

    override suspend fun run() {
        logger.debug { "portfolios: enumerating ${Portfolios.all.size} portfolios and probing each" }
        val portfolios = Portfolios.all
        if (portfolios.isEmpty()) {
            echo("No verifier portfolio registered.")
            return
        }

        for (portfolio in portfolios) {
            val availability = portfolio.availability()
            val tag = when (availability) {
                AvailabilityReport.Available -> "Available"
                is AvailabilityReport.Unavailable -> "Unavailable (${availability.reason})"
            }
            echo("${portfolio.id}  -  ${portfolio.displayName}  $tag")
            echo("    ${portfolio.description}")
            portfolio.legs.forEach {
                echo("    leg: ${it.id} (${it.soundness})")
            }
            if (availability is AvailabilityReport.Unavailable && availability.hints.isNotEmpty()) {
                availability.hints.forEach { echo("    hint: $it") }
            }
        }
    }
}
