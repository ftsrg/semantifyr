/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

private val NON_FS_SAFE = Regex("[^A-Za-z0-9._-]")

fun qualifiedNameToDirectoryName(qualifiedName: String): String {
    return qualifiedName.replace("::", ".").replace(NON_FS_SAFE, "_")
}
