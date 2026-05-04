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
import hu.bme.mit.semantifyr.verification.portfolio.SingleBackendPortfolio
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio

object Portfolios {
    private val logger by loggerFactory()

    private val theta = ThetaBackend()
    private val nuxmv = NuxmvBackend()
    private val uppaal = UppaalBackend()
    private val spin = SpinBackend()

    val SmartFull: VerificationPortfolio = SmartFullPortfolio()
    val AllAgree: VerificationPortfolio = AllAgreeFullPortfolio()

    val ThetaFull: VerificationPortfolio = ThetaFullPortfolio()
    val ThetaCegarExpl: VerificationPortfolio = SingleBackendPortfolio(theta, ThetaConfig.CegarExpl)
    val ThetaCegarExplPredCombined: VerificationPortfolio = SingleBackendPortfolio(theta, ThetaConfig.CegarExplPredCombined)
    val ThetaCegarPredCart: VerificationPortfolio = SingleBackendPortfolio(theta, ThetaConfig.CegarPredCart)
    val ThetaBoundedKInduction: VerificationPortfolio = SingleBackendPortfolio(theta, ThetaConfig.BoundedKInduction)
    val ThetaBoundedBmc: VerificationPortfolio = SingleBackendPortfolio(theta, ThetaConfig.BoundedBmc)
    val ThetaBoundedImc: VerificationPortfolio = SingleBackendPortfolio(theta, ThetaConfig.BoundedImc)
    val ThetaIc3: VerificationPortfolio = SingleBackendPortfolio(theta, ThetaConfig.Ic3)

    val UppaalDefault: VerificationPortfolio = SingleBackendPortfolio(uppaal, UppaalConfig.Default)
    val UppaalBreadthFirst: VerificationPortfolio = SingleBackendPortfolio(uppaal, UppaalConfig.BreadthFirst)
    val UppaalDepthFirst: VerificationPortfolio = SingleBackendPortfolio(uppaal, UppaalConfig.DepthFirst)
    val UppaalRandomDepthFirst: VerificationPortfolio = SingleBackendPortfolio(uppaal, UppaalConfig.RandomDepthFirst)
    val UppaalOverApproximation: VerificationPortfolio = SingleBackendPortfolio(uppaal, UppaalConfig.OverApproximation)
    val UppaalUnderApproximation: VerificationPortfolio = SingleBackendPortfolio(uppaal, UppaalConfig.UnderApproximation)
    val UppaalUnderApproximationLarge: VerificationPortfolio = SingleBackendPortfolio(uppaal, UppaalConfig.UnderApproximationLarge)
    val UppaalAggressiveInclusion: VerificationPortfolio = SingleBackendPortfolio(uppaal, UppaalConfig.AggressiveInclusion)

    val NuxmvIc3Invar: VerificationPortfolio = SingleBackendPortfolio(nuxmv, NuxmvConfig.Ic3Invar)
    val NuxmvBmcInvar: VerificationPortfolio = SingleBackendPortfolio(nuxmv, NuxmvConfig.BmcInvar)
    val NuxmvBddInvar: VerificationPortfolio = SingleBackendPortfolio(nuxmv, NuxmvConfig.BddInvar)

    val SpinExhaustiveDfs: VerificationPortfolio = SingleBackendPortfolio(spin, SpinConfig.ExhaustiveDfs)
    val SpinExhaustiveBfs: VerificationPortfolio = SingleBackendPortfolio(spin, SpinConfig.ExhaustiveBfs)
    val SpinSafeDfs: VerificationPortfolio = SingleBackendPortfolio(spin, SpinConfig.SafeDfs)
    val SpinFastDfs: VerificationPortfolio = SingleBackendPortfolio(spin, SpinConfig.FastDfs)
    val SpinCollapse: VerificationPortfolio = SingleBackendPortfolio(spin, SpinConfig.Collapse)
    val SpinBitstate: VerificationPortfolio = SingleBackendPortfolio(spin, SpinConfig.BitstateHashing)
    val SpinHashCompact: VerificationPortfolio = SingleBackendPortfolio(spin, SpinConfig.HashCompact)

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
