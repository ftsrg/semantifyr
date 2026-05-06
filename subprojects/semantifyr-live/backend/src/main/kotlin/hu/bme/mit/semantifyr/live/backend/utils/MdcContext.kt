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

class MdcContext(
    private val contextMap: Map<String, String>,
) : AbstractCoroutineContextElement(Key),
    ThreadContextElement<Map<String, String>?> {

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

fun currentMdcContext(): MdcContext {
    val snapshot = MDC.getCopyOfContextMap() ?: emptyMap()
    return MdcContext(snapshot)
}
