/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.witness.Fraction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReportTypeKind {
    @SerialName("datatype")
    Datatype,

    @SerialName("enum")
    Enum,

    @SerialName("class")
    Class,

    @SerialName("record")
    Record,

    @SerialName("feature")
    Feature,
}

@Serializable
data class ReportType(
    val kind: ReportTypeKind,
    val name: String,
)

@Serializable
data class ObservedParameter(
    val parameter: String,
    val type: ReportType,
)

@Serializable
data class ObservedTransition(
    val transition: String,
    val parameters: List<ObservedParameter>,
)

@Serializable
data class ObservedSlot(
    val array: String,
    val index: Int,
    val size: Int,
)

@Serializable
data class ObservedVariable(
    val variable: String,
    val type: ReportType,
    val slot: ObservedSlot? = null,
)

@Serializable
data class ReportCall(
    val self: String,
    val transition: String,
    val arguments: Map<String, ReportValue>,
    val calls: List<ReportCall>,
)

@Serializable
data class ReportInitialEntry(
    val state: Int,
    val calls: List<ReportCall>,
)

@Serializable
data class ReportState(
    val values: Map<String, ReportValue>,
)

@Serializable
data class ReportStep(
    val source: Int,
    val target: Int,
    @Serializable(with = FractionSerializer::class)
    val elapsed: Fraction,
    val calls: List<ReportCall>,
)

@Serializable
data class ReportTrace(
    val root: String,
    val transitions: List<ObservedTransition>,
    val variables: List<ObservedVariable>,
    val initial: ReportInitialEntry,
    val states: List<ReportState>,
    val steps: List<ReportStep>,
) {
    init {
        require(initial.state in states.indices) {
            "the initial entry names the state ${initial.state}, which the trace does not hold"
        }
        for (step in steps) {
            require(step.source in states.indices && step.target in states.indices) {
                "a step goes from the state ${step.source} to ${step.target}, which the trace does not hold"
            }
        }
        val declared = variables.map {
            it.variable
        }.toSet()
        for (state in states) {
            for ((key, value) in state.values) {
                require(key in declared) {
                    "a state holds $key, which the trace does not list among its variables"
                }
                require(value !is ReportValue.Array) {
                    "a state holds the array $key whole instead of slot by slot"
                }
            }
        }
    }
}
