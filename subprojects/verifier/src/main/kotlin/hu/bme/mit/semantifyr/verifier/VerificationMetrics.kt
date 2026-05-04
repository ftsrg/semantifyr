/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.BackendMetrics
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class VerifierMetrics(
    val compilationDuration: Duration = Duration.ZERO,
    val backAnnotationDuration: Duration = Duration.ZERO,
    val portfolioDuration: Duration = Duration.ZERO,
)

@Serializable
data class VerificationMetrics(
    val totalDuration: Duration = Duration.ZERO,
    val backend: BackendMetrics? = null,
    val verifier: VerifierMetrics = VerifierMetrics(),
)
