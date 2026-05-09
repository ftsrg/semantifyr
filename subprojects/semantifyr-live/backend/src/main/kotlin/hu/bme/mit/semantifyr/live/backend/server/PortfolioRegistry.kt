/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio

object PortfolioRegistry {

    data class Entry(val displayName: String, val portfolio: VerificationPortfolio)

    val entries: List<Entry> = listOf(
        Entry("Auto", Portfolios.SmartFull),
        Entry("All agree", Portfolios.AllAgree),
        Entry("Theta", Portfolios.ThetaFull),
        Entry("nuXmv", Portfolios.NuxmvIc3Invar),
        Entry("Spin", Portfolios.SpinSafeDfs),
        Entry("Uppaal", Portfolios.UppaalDefault),
    )

    fun snapshot(environment: ExecutionEnvironment = ExecutionEnvironment.Empty): List<PortfolioResponse> {
        return entries.map {
            val available = it.portfolio.availability(environment) is AvailabilityReport.Available
            PortfolioResponse(
                id = it.portfolio.id,
                displayName = it.displayName,
                description = it.portfolio.description,
                available = available,
            )
        }
    }
}
