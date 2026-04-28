/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.backend.VerificationMetrics
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import kotlinx.serialization.Serializable

@Serializable
data class VerificationReport(
    val case: VerificationCaseReport,
    val portfolio: PortfolioReport,
    val configuration: ConfigurationReport,
    val verification: VerificationOutcomeReport,
    val artifacts: ArtifactPointersReport,
)

@Serializable
data class VerificationCaseReport(
    val qualifiedName: String,
    val className: String?,
)

@Serializable
data class PortfolioReport(
    val id: String,
    val displayName: String,
    val familyId: String,
)

@Serializable
data class ConfigurationReport(
    val optimization: OptimizationConfig,
    val timeout: String,
    val environment: String,
)

@Serializable
data class VerificationOutcomeReport(
    val verdict: VerificationVerdict,
    val message: String?,
    val metadata: VerificationRunMetadata,
    val metrics: VerificationMetrics,
)

@Serializable
data class ArtifactPointersReport(
    val inputModel: String? = null,
    val instantiatedModel: String? = null,
    val inlinedModel: String? = null,
    val flattenedModel: String? = null,
    val witness: String? = null,
    val trace: String? = null,
    val mapping: String? = null,
    val backendDirectories: List<String> = emptyList(),
)
