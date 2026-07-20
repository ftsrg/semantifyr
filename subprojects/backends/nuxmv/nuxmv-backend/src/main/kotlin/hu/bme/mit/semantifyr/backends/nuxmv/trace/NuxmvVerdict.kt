/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.trace

enum class NuxmvVerdict {
    True,
    False,
    ;

    fun invert(): NuxmvVerdict {
        return if (this == True) {
            False
        } else {
            True
        }
    }
}
