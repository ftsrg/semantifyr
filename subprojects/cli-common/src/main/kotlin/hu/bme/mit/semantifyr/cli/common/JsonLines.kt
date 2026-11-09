/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration

val jsonLineFormat = Json {
    encodeDefaults = true
}

@Serializable
data class VerifyOutcomeLine(
    val case: String,
    val exitCode: Int,
    val report: String,
    val compilationDuration: Duration,
    val verificationDuration: Duration,
)

@Serializable
data class CompilationFailureLine(
    val case: String,
    val exitCode: Int,
    val error: String?,
)

@Serializable
data class BackAnnotationFailureLine(
    val case: String,
    val exitCode: Int,
    val error: String?,
)

@Serializable
data class MetricsLine(
    val case: String,
    val inputSize: ModelSize,
    val inlinedSize: ModelSize,
    val flattenedSize: ModelSize,
)
