/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.portfolios.Portfolios

class PortfoliosCommand : SuspendingCliktCommand("portfolios") {
    private val logger by loggerFactory()

    override fun help(context: Context): String {
        return "List the portfolios, the strategies for the backend runs, and whether each can run on this machine."
    }

    override suspend fun run() {
        logger.debug { "portfolios: enumerating ${Portfolios.all.size} portfolios and probing each" }
        for (portfolio in Portfolios.all) {
            val availability = portfolio.availability()
            val state = when (availability) {
                AvailabilityReport.Available -> "available"
                is AvailabilityReport.Unavailable -> "unavailable: ${availability.reason}"
            }
            echo("${portfolio.id}  ${portfolio.displayName}  $state")
            echo("    ${portfolio.description}")
            for (leg in portfolio.legs) {
                echo("    ${leg.id.replace(':', ' ')}, ${soundnessWords(leg.soundness)}")
            }
            if (availability is AvailabilityReport.Unavailable) {
                for (hint in availability.hints) {
                    echo("    hint: $hint")
                }
            }
        }
    }

    private fun soundnessWords(soundness: SoundnessDirection): String {
        return when (soundness) {
            SoundnessDirection.Exact -> "exact"
            SoundnessDirection.OverApproximating -> "over-approximating"
            SoundnessDirection.UnderApproximating -> "under-approximating"
        }
    }
}
