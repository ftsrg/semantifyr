/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient

interface SessionLanguageClient : LanguageClient {

    @JsonNotification(SemantifyrLiveMethods.VERIFICATIONS_CHANGED)
    fun verificationsChanged(params: VerificationsChangedParams)
}
