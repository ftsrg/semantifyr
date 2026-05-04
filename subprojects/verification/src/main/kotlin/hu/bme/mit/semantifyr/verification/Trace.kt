/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verification.witness.CallTrace
import hu.bme.mit.semantifyr.verification.witness.WitnessState

class Trace(
    val backAnnotatedModel: InlinedOxsts,
    val witnessState: WitnessState,
    val callTrace: CallTrace,
)
