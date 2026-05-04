/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration

data class VerificationCase(
    val qualifiedName: String,
    val classDeclaration: ClassDeclaration,
    val tags: Set<String> = emptySet(),
) {
    override fun toString(): String {
        val tagSuffix = if (tags.isEmpty()) {
            ""
        } else {
            "@[${tags.joinToString(",")}]"
        }
        return "$qualifiedName$tagSuffix"
    }
}
