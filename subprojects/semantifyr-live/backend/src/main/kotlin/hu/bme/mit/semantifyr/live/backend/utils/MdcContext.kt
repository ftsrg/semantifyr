/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.utils

import kotlinx.coroutines.ThreadContextElement
import org.slf4j.MDC
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element that propagates SLF4J MDC values across coroutine
 * suspensions and thread switches. The MDC state is captured at creation time
 * and restored whenever the coroutine resumes on a thread.
 *
 * Usage:
 * ```
 * withContext(MdcContext("sessionId" to sessionId)) {
 *     // all log calls here (and in child coroutines) will have sessionId in MDC
 * }
 * ```
 */
class MdcContext(
    private val contextMap: Map<String, String>,
) : AbstractCoroutineContextElement(Key), ThreadContextElement<Map<String, String>?> {

    companion object Key : CoroutineContext.Key<MdcContext>

    constructor(vararg pairs: Pair<String, String>) : this(pairs.toMap())

    override fun updateThreadContext(context: CoroutineContext): Map<String, String>? {
        val oldState = MDC.getCopyOfContextMap()
        for ((key, value) in contextMap) {
            MDC.put(key, value)
        }
        return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String>?) {
        if (oldState == null) {
            MDC.clear()
        } else {
            MDC.setContextMap(oldState)
        }
    }
}
