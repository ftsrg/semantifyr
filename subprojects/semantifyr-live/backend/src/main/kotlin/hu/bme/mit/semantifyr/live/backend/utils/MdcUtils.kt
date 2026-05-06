/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.utils

import org.slf4j.MDC

inline fun <T> withRequestId(requestId: String, block: () -> T): T {
    val oldState = MDC.getCopyOfContextMap()
    MDC.put("requestId", requestId)
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
