/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import kotlinx.serialization.Serializable

// no longer written, read only by the website's artifacts reference until it describes the report
@Serializable
data class TraceArgument(
    val parameter: String,
    val value: String,
)

@Serializable
data class TraceEntry(
    val self: String,
    val calledTransition: String,
    val arguments: List<TraceArgument>,
    val innerTraces: List<TraceEntry>?,
)

@Serializable
data class CallTraceStep(
    val traces: List<TraceEntry>,
)

@Serializable
data class CallTrace(
    val initializedStep: CallTraceStep,
    val steps: List<CallTraceStep>,
)

@Serializable
data class WitnessStateValue(
    val variable: String,
    val value: String,
)

@Serializable
data class WitnessStateStep(
    val values: List<WitnessStateValue>,
)

@Serializable
data class WitnessState(
    val initializedStep: WitnessStateStep,
    val steps: List<WitnessStateStep>,
)
