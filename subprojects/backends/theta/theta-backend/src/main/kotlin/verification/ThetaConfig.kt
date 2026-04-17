/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification

data class ThetaConfig(
    val id: String,
    val parameters: String,
) {
    companion object {
        val CegarExpl = ThetaConfig(
            id = "cegar-expl",
            parameters = "CEGAR --domain EXPL --flatten-depth 0 --refinement SEQ_ITP --maxenum 250 --initprec CTRL --stacktrace",
        )
        val CegarExplPredCombined = ThetaConfig(
            id = "cegar-expl-pred-combined",
            parameters = "CEGAR --domain EXPL_PRED_COMBINED --flatten-depth 0 --autoexpl NEWOPERANDS --initprec CTRL --stacktrace",
        )
        val CegarPredCart = ThetaConfig(
            id = "cegar-pred-cart",
            parameters = "CEGAR --domain PRED_CART --flatten-depth 0 --refinement SEQ_ITP --stacktrace",
        )
        val BoundedKInduction = ThetaConfig(
            id = "bounded-kinduction",
            parameters = "BOUNDED --flatten-depth 0 --variant KINDUCTION --stacktrace",
        )

        val Builtin: List<ThetaConfig> = listOf(
            CegarExpl,
            CegarExplPredCombined,
            CegarPredCart,
            BoundedKInduction,
        )

        fun builtinById(id: String): ThetaConfig? {
            return Builtin.firstOrNull { it.id == id }
        }

        fun custom(parameters: String): ThetaConfig {
            return ThetaConfig(id = "custom", parameters = parameters)
        }
    }
}
