/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backends.theta.verification.ThetaConfig
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.semantics.verification.portfolio.Portfolio

object Portfolios {

    private val logger by loggerFactory()

    val ThetaFull: Portfolio = ThetaFullPortfolio()
    val ThetaCegarExpl: Portfolio = ThetaSinglePortfolio(ThetaConfig.CegarExpl)
    val ThetaCegarExplPredCombined: Portfolio = ThetaSinglePortfolio(ThetaConfig.CegarExplPredCombined)
    val ThetaCegarPredCart: Portfolio = ThetaSinglePortfolio(ThetaConfig.CegarPredCart)
    val ThetaBoundedKInduction: Portfolio = ThetaSinglePortfolio(ThetaConfig.BoundedKInduction)

    val all: List<Portfolio> = listOf(
        ThetaFull,
        ThetaCegarExpl,
        ThetaCegarExplPredCombined,
        ThetaCegarPredCart,
        ThetaBoundedKInduction,
    )

    fun byId(id: String): Portfolio? {
        val match = all.firstOrNull { it.id == id }
        if (match == null) {
            logger.warn { "Unknown portfolio '$id' (known: ${all.joinToString { it.id }})" }
        } else {
            logger.debug { "Resolved portfolio '$id'" }
        }
        return match
    }
}
