/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.utils

import org.slf4j.MDC

inline fun <T> withSessionId(sessionId: String, block: () -> T): T {
    return withMdcValue("sessionId" to sessionId) {
        block()
    }
}

inline fun <T> withRequestId(requestId: String, block: () -> T): T {
    return withMdcValue("requestId" to requestId) {
        block()
    }
}

inline fun <T> withMdcValue(vararg pairs: Pair<String, String>, block: () -> T): T {
    return withMdcValue(pairs.toMap(), block)
}

inline fun <T> withMdcValue(contextMap: Map<String, String>, block: () -> T): T {
    val oldState = MDC.getCopyOfContextMap()
    for ((key, value) in contextMap) {
        MDC.put(key, value)
    }
    try {
        return block()
    } finally {
        if (oldState == null) {
            MDC.clear()
        } else {
            MDC.setContextMap(oldState)
        }
    }
}
