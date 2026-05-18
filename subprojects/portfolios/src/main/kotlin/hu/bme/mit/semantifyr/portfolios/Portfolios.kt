/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackend
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackend
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.backends.theta.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio

object Portfolios {
    private val logger by loggerFactory()

    val SmartFull: VerificationPortfolio = SmartFullPortfolio()
    val AllAgree: VerificationPortfolio = AllAgreeFullPortfolio()

    val ThetaFull: VerificationPortfolio = ThetaFullPortfolio()
    val ThetaCegarExpl: VerificationPortfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarExpl)
    val ThetaCegarExplPredCombined: VerificationPortfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarExplPredCombined)
    val ThetaCegarPredCart: VerificationPortfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarPredCart)
    val ThetaBoundedKInduction: VerificationPortfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.BoundedKInduction)
    val ThetaBoundedBmc: VerificationPortfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.BoundedBmc)
    val ThetaBoundedImc: VerificationPortfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.BoundedImc)
    val ThetaIc3: VerificationPortfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.Ic3)

    val UppaalDefault: VerificationPortfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.Default)
    val UppaalBreadthFirst: VerificationPortfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.BreadthFirst)
    val UppaalDepthFirst: VerificationPortfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.DepthFirst)
    val UppaalRandomDepthFirst: VerificationPortfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.RandomDepthFirst)
    val UppaalOverApproximation: VerificationPortfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.OverApproximation)
    val UppaalUnderApproximation: VerificationPortfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.UnderApproximation)
    val UppaalUnderApproximationLarge: VerificationPortfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.UnderApproximationLarge)
    val UppaalAggressiveInclusion: VerificationPortfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.AggressiveInclusion)

    val NuxmvIc3Invar: VerificationPortfolio = SingleBackendPortfolio(NuxmvBackend(), NuxmvConfig.Ic3Invar)
    val NuxmvBmcInvar: VerificationPortfolio = SingleBackendPortfolio(NuxmvBackend(), NuxmvConfig.BmcInvar)
    val NuxmvBddInvar: VerificationPortfolio = SingleBackendPortfolio(NuxmvBackend(), NuxmvConfig.BddInvar)

    val SpinExhaustiveDfs: VerificationPortfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.ExhaustiveDfs)
    val SpinExhaustiveBfs: VerificationPortfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.ExhaustiveBfs)
    val SpinSafeDfs: VerificationPortfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.SafeDfs)
    val SpinFastDfs: VerificationPortfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.FastDfs)
    val SpinCollapse: VerificationPortfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.Collapse)
    val SpinBitstate: VerificationPortfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.BitstateHashing)
    val SpinHashCompact: VerificationPortfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.HashCompact)

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
