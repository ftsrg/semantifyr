/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

const val REPORT_VERSION = 2

@Serializable
enum class ReportKind {
    @SerialName("run")
    Run,

    @SerialName("re-check")
    Recheck,
}

@Serializable
data class VerificationReport(
    val version: Int,
    val kind: ReportKind,
    val verificationCase: String,
    val portfolioId: String,
    val optimization: OptimizationConfig,
    val timeout: Duration,
    val result: VerificationResult,
    val trace: ReportTrace? = null,
)
