/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Inject
import org.eclipse.lsp4j.services.LanguageClient

@SessionScoped
class SessionClient @Inject constructor() {

    private lateinit var languageClient: LanguageClient

    fun attach(client: LanguageClient) {
        languageClient = client
    }

    fun get(): LanguageClient {
        return languageClient
    }
}
