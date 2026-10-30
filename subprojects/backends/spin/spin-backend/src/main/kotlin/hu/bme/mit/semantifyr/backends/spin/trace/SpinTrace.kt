/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.trace

data class SpinTraceState(
    val variableValues: Map<String, String>,
)

data class SpinTrace(
    val states: List<SpinTraceState>,
)
