/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

enum class SysMLv2Variant(
    internal val resourcePrefix: String,
) {
    Default("default"),
    TopDown("topdown"),
}
