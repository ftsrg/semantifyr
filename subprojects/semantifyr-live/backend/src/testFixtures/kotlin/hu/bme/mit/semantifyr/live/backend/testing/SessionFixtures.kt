/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.Language
import hu.bme.mit.semantifyr.live.backend.WorkspaceLayout

fun testFlavor(
    id: String = "oxsts",
    fileName: String = "snippet.oxsts",
    language: Language = Language.Oxsts,
    workspaceLayout: WorkspaceLayout = WorkspaceLayout.SingleFile,
    verificationCommand: String = "oxsts.case.verify",
    validateWitnessCommand: String? = "oxsts.case.validateWitness",
    discoveryCommand: String = "oxsts.case.discover",
): Flavor {
    return Flavor(
        id = id,
        displayName = "Semantifyr",
        fileName = fileName,
        language = language,
        workspaceLayout = workspaceLayout,
        verificationCommand = verificationCommand,
        validateWitnessCommand = validateWitnessCommand,
        discoveryCommand = discoveryCommand,
    )
}
