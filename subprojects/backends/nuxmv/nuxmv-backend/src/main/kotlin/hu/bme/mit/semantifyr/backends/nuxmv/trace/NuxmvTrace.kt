/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.trace

data class NuxmvTraceState(
    val variableValues: Map<String, String>,
)

data class NuxmvTrace(
    val states: List<NuxmvTraceState>,
)
