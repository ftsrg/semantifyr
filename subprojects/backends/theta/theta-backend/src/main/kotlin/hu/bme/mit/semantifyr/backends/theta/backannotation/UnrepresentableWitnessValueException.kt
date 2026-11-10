/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import java.math.BigInteger

class UnrepresentableWitnessValueException(message: String) : WitnessFailedException(message)

fun BigInteger.fitsInt(): Boolean {
    return bitLength() < Int.SIZE_BITS
}
