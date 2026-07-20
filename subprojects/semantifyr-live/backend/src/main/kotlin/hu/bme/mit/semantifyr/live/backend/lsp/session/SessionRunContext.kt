/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.lsp.service.SharedExecutorProvider
import kotlinx.coroutines.CoroutineScope
import kotlin.time.TimeSource

@SessionScoped
class SessionRunContext @Inject constructor(
    sharedExecutorProvider: SharedExecutorProvider,
) {
    val coroutineScope = CoroutineScope(sharedExecutorProvider.dispatcher)

    val startMark = TimeSource.Monotonic.markNow()
}
