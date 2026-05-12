/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import java.nio.file.Path

sealed class WorkspaceLayout {
    data object SingleFile : WorkspaceLayout()
    data class WithLibrary(
        val libraryRelativePath: Path,
        val workspaceTargetName: String,
    ) : WorkspaceLayout()
}

enum class Language(val id: String) {
    Oxsts("oxsts"),
    Gamma("gamma"),
}

data class Flavor(
    val id: String,
    val displayName: String,
    val fileName: String,
    val language: Language,
    val workspaceLayout: WorkspaceLayout,
    val verificationCommand: String,
    val validateWitnessCommand: String? = null,
    val discoveryCommand: String,
    val peekCompiledOutput: Boolean = false,
)

object FlavorRegistry {
    val flavors = listOf(
        Flavor(
            id = "oxsts",
            displayName = "Semantifyr",
            fileName = "snippet.oxsts",
            language = Language.Oxsts,
            workspaceLayout = WorkspaceLayout.SingleFile,
            verificationCommand = "oxsts.case.verify",
            validateWitnessCommand = "oxsts.case.validateWitness",
            discoveryCommand = "oxsts.case.discover",
        ),
        Flavor(
            id = "oxsts-with-gamma-library",
            displayName = "Semantifyr with Gamma library",
            fileName = "snippet.oxsts",
            language = Language.Oxsts,
            workspaceLayout = WorkspaceLayout.WithLibrary(
                libraryRelativePath = Path.of("gamma"),
                workspaceTargetName = "Library",
            ),
            verificationCommand = "oxsts.case.verify",
            validateWitnessCommand = "oxsts.case.validateWitness",
            discoveryCommand = "oxsts.case.discover",
        ),
        Flavor(
            id = "oxsts-with-sysmlv2-library",
            displayName = "Semantifyr with SysML v2 library",
            fileName = "snippet.oxsts",
            language = Language.Oxsts,
            workspaceLayout = WorkspaceLayout.WithLibrary(
                libraryRelativePath = Path.of("sysmlv2"),
                workspaceTargetName = "Library",
            ),
            verificationCommand = "oxsts.case.verify",
            validateWitnessCommand = "oxsts.case.validateWitness",
            discoveryCommand = "oxsts.case.discover",
        ),
        Flavor(
            id = "gamma",
            displayName = "Gamma",
            fileName = "snippet.gamma",
            language = Language.Gamma,
            workspaceLayout = WorkspaceLayout.WithLibrary(
                libraryRelativePath = Path.of("gamma"),
                workspaceTargetName = "Library",
            ),
            verificationCommand = "gamma.case.verify",
            discoveryCommand = "gamma.case.discover",
            peekCompiledOutput = true,
        ),
    )

    private val byId = flavors.associateBy {
        it.id
    }

    fun get(id: String): Flavor? {
        return byId[id]
    }

}
