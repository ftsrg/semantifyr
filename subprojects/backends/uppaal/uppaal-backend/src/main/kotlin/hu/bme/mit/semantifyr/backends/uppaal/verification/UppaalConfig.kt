/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.verification

import hu.bme.mit.semantifyr.backend.BackendConfig

data class UppaalConfig(
    override val id: String,
    val parameters: String,
) : BackendConfig {
    companion object {
        private const val TRACE_FLAG = "-t 1"

        private fun withTraceFlag(parameters: String): String {
            return if (parameters.isBlank()) TRACE_FLAG else "$TRACE_FLAG $parameters"
        }

        val Default = UppaalConfig(
            id = "default",
            parameters = TRACE_FLAG,
        )

        val BreadthFirst = UppaalConfig(
            id = "breadth-first",
            parameters = withTraceFlag("-o 0"),
        )
        val DepthFirst = UppaalConfig(
            id = "depth-first",
            parameters = withTraceFlag("-o 1"),
        )
        val RandomDepthFirst = UppaalConfig(
            id = "random-depth-first",
            parameters = withTraceFlag("-o 2"),
        )
        val OverApproximation = UppaalConfig(
            id = "over-approximation",
            parameters = withTraceFlag("-A"),
        )
        val UnderApproximation = UppaalConfig(
            id = "under-approximation",
            parameters = withTraceFlag("-Z"),
        )
        val UnderApproximationLarge = UppaalConfig(
            id = "under-approximation-large",
            parameters = withTraceFlag("-Z -H 27"),
        )
        val AggressiveInclusion = UppaalConfig(
            id = "aggressive-inclusion",
            parameters = withTraceFlag("-S 2"),
        )

        val Builtin: List<UppaalConfig> = listOf(
            Default,
            BreadthFirst,
            DepthFirst,
            RandomDepthFirst,
            OverApproximation,
            UnderApproximation,
            UnderApproximationLarge,
            AggressiveInclusion,
        )
    }
}
