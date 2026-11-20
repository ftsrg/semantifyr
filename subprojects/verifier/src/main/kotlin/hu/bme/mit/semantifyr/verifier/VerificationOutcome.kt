/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import java.nio.file.Path

class VerificationOutcome(
    val report: VerificationReport,
    val reportPath: Path,
    val witness: VerificationWitness?,
)

class VerificationWitness(
    val model: InlinedOxsts,
    val path: Path,
)
