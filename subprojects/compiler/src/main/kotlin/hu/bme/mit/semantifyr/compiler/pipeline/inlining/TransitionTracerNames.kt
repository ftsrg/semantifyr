/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

object TransitionTracerNames {
    const val TRACER_PREFIX = "__transition_tracer"

    fun tracerName(index: Int): String {
        return "$TRACER_PREFIX$index"
    }

    fun isTracerName(name: String): Boolean {
        return name.startsWith(TRACER_PREFIX)
    }
}
