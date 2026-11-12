/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

enum class SessionKind(val id: String) {
    Full("full"),
    Embed("embed"),
    EditOnly("edit-only"),
    ;

    companion object {
        fun fromId(id: String?): SessionKind {
            return entries.firstOrNull {
                it.id == id
            } ?: Full
        }
    }
}
