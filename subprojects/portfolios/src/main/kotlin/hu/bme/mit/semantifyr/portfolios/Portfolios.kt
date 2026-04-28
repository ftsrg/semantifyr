/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio

object Portfolios {
    private val logger by loggerFactory()

    val SmartFull: VerificationPortfolio = SmartFullPortfolio()
    val AllAgree: VerificationPortfolio = AllAgreeFullPortfolio()

    val ThetaFull: VerificationPortfolio = ThetaFullPortfolio()
    val ThetaCegarExpl: VerificationPortfolio = ThetaSinglePortfolio(ThetaConfig.CegarExpl)
    val ThetaCegarExplPredCombined: VerificationPortfolio = ThetaSinglePortfolio(ThetaConfig.CegarExplPredCombined)
    val ThetaCegarPredCart: VerificationPortfolio = ThetaSinglePortfolio(ThetaConfig.CegarPredCart)
    val ThetaBoundedKInduction: VerificationPortfolio = ThetaSinglePortfolio(ThetaConfig.BoundedKInduction)
    val ThetaBoundedBmc: VerificationPortfolio = ThetaSinglePortfolio(ThetaConfig.BoundedBmc)
    val ThetaBoundedImc: VerificationPortfolio = ThetaSinglePortfolio(ThetaConfig.BoundedImc)
    val ThetaIc3: VerificationPortfolio = ThetaSinglePortfolio(ThetaConfig.Ic3)

    val UppaalDefault: VerificationPortfolio = UppaalSinglePortfolio(UppaalConfig.Default)
    val UppaalBreadthFirst: VerificationPortfolio = UppaalSinglePortfolio(UppaalConfig.BreadthFirst)
    val UppaalDepthFirst: VerificationPortfolio = UppaalSinglePortfolio(UppaalConfig.DepthFirst)
    val UppaalRandomDepthFirst: VerificationPortfolio = UppaalSinglePortfolio(UppaalConfig.RandomDepthFirst)
    val UppaalOverApproximation: VerificationPortfolio = UppaalSinglePortfolio(UppaalConfig.OverApproximation)
    val UppaalUnderApproximation: VerificationPortfolio = UppaalSinglePortfolio(UppaalConfig.UnderApproximation)
    val UppaalUnderApproximationLarge: VerificationPortfolio = UppaalSinglePortfolio(UppaalConfig.UnderApproximationLarge)
    val UppaalAggressiveInclusion: VerificationPortfolio = UppaalSinglePortfolio(UppaalConfig.AggressiveInclusion)

    val NuxmvIc3Invar: VerificationPortfolio = NuxmvSinglePortfolio(NuxmvConfig.Ic3Invar)
    val NuxmvBmcInvar: VerificationPortfolio = NuxmvSinglePortfolio(NuxmvConfig.BmcInvar)
    val NuxmvBddInvar: VerificationPortfolio = NuxmvSinglePortfolio(NuxmvConfig.BddInvar)

    val SpinExhaustiveDfs: VerificationPortfolio = SpinSinglePortfolio(SpinConfig.ExhaustiveDfs)
    val SpinExhaustiveBfs: VerificationPortfolio = SpinSinglePortfolio(SpinConfig.ExhaustiveBfs)
    val SpinSafeDfs: VerificationPortfolio = SpinSinglePortfolio(SpinConfig.SafeDfs)
    val SpinFastDfs: VerificationPortfolio = SpinSinglePortfolio(SpinConfig.FastDfs)
    val SpinCollapse: VerificationPortfolio = SpinSinglePortfolio(SpinConfig.Collapse)
    val SpinBitstate: VerificationPortfolio = SpinSinglePortfolio(SpinConfig.BitstateHashing)
    val SpinHashCompact: VerificationPortfolio = SpinSinglePortfolio(SpinConfig.HashCompact)

    val all: List<VerificationPortfolio> = listOf(
        SmartFull,
        AllAgree,
        ThetaFull,
        ThetaCegarExpl,
        ThetaCegarExplPredCombined,
        ThetaCegarPredCart,
        ThetaBoundedKInduction,
        ThetaBoundedBmc,
        ThetaBoundedImc,
        ThetaIc3,
        UppaalDefault,
        UppaalBreadthFirst,
        UppaalDepthFirst,
        UppaalRandomDepthFirst,
        UppaalOverApproximation,
        UppaalUnderApproximation,
        UppaalUnderApproximationLarge,
        UppaalAggressiveInclusion,
        NuxmvIc3Invar,
        NuxmvBmcInvar,
        NuxmvBddInvar,
        SpinExhaustiveDfs,
        SpinExhaustiveBfs,
        SpinSafeDfs,
        SpinFastDfs,
        SpinCollapse,
        SpinBitstate,
        SpinHashCompact,
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
