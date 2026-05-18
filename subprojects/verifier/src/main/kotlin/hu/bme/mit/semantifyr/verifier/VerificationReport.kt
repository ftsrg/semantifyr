/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class VerificationReport(
    val verificationCase: String,
    val portfolioId: String,
    val optimization: OptimizationConfig,
    val timeout: Duration,
    val result: VerificationResult,
)
