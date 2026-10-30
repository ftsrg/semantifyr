/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.trace

enum class SpinVerdict {
    True,
    False,
    ;

    fun invert(): SpinVerdict {
        return if (this == True) {
            False
        } else {
            True
        }
    }
}
