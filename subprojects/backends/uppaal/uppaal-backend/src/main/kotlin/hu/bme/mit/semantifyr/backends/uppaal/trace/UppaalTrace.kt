/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.trace

data class UppaalTraceState(
    val locations: List<String>,
    val variableValues: Map<String, String>,
)

data class UppaalTrace(
    val states: List<UppaalTraceState>,
)
