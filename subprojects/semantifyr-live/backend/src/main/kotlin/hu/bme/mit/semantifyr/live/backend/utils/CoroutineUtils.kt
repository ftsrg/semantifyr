/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import org.eclipse.xtext.util.CancelIndicator

class CoroutineScopeCancelIndicator(
    private val coroutineScope: CoroutineScope,
) : CancelIndicator {
    override fun isCanceled(): Boolean {
        return !coroutineScope.isActive
    }
}

fun CoroutineScope.coroutineScopeCancelIndicator(): CoroutineScopeCancelIndicator {
    return CoroutineScopeCancelIndicator(this)
}
