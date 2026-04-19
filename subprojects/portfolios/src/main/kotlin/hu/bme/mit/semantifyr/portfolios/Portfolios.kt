/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backends.theta.hu.bme.mit.semantifyr.verification.ThetaConfig
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio

object Portfolios {

    private val logger by loggerFactory()

    val ThetaFull: VerificationPortfolio = ThetaFullPortfolio()
    val ThetaCegarExpl: VerificationPortfolio = ThetaSinglePortfolio(ThetaConfig.CegarExpl)
    val ThetaCegarExplPredCombined: VerificationPortfolio = ThetaSinglePortfolio(ThetaConfig.CegarExplPredCombined)
    val ThetaCegarPredCart: VerificationPortfolio = ThetaSinglePortfolio(ThetaConfig.CegarPredCart)
    val ThetaBoundedKInduction: VerificationPortfolio = ThetaSinglePortfolio(ThetaConfig.BoundedKInduction)

    val all: List<VerificationPortfolio> = listOf(
        ThetaFull,
        ThetaCegarExpl,
        ThetaCegarExplPredCombined,
        ThetaCegarPredCart,
        ThetaBoundedKInduction,
    )

    fun byIdOrNull(id: String): VerificationPortfolio? {
        val match = all.firstOrNull { it.id == id }
        if (match == null) {
            logger.warn { "Unknown portfolio '$id' (known: ${all.joinToString { it.id }})" }
        } else {
            logger.debug { "Resolved portfolio '$id'" }
        }
        return match
    }

}
