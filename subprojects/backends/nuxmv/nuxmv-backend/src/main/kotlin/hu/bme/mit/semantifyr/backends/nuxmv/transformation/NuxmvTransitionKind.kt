/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

enum class NuxmvTransitionKind(val tagName: String) {
    Init("init"),
    Tran("tran"),
}
