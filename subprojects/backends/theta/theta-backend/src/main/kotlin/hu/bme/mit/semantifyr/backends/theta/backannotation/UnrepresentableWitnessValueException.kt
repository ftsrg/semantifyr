/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import java.math.BigInteger

class UnrepresentableWitnessValueException(message: String) : Exception(message)

fun BigInteger.fitsInt(): Boolean {
    return bitLength() < Int.SIZE_BITS
}
