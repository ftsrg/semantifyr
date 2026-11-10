/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.oxsts.lang.ide.client.SemantifyrLanguageClient
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification

interface SessionLanguageClient : SemantifyrLanguageClient {

    @JsonNotification(SemantifyrLiveMethods.VERIFICATIONS_CHANGED)
    fun verificationsChanged(params: VerificationsChangedParams)
}
