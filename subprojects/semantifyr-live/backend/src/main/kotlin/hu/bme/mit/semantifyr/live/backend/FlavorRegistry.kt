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
    val description: String,
    val fileName: String,
    val language: Language,
    val workspaceLayout: WorkspaceLayout,
    val verificationCommand: String,
    val validateWitnessCommand: String? = null,
    val validateWitnessByCase: Boolean = false,
    val discoveryCommand: String,
    val compiledOxstsCommand: String? = null,
    val navigationCommands: List<String> = emptyList(),
    // the editor languages a session of this flavor serves; the SysML v2 library flavor serves the OXSTS a SysML model maps to
    val editorLanguageIds: List<String> = listOf(language.id),
) {
    fun sessionLanguageOf(requested: String?): String {
        return editorLanguageIds.firstOrNull {
            it == requested
        } ?: language.id
    }

    val commands: List<String>
        get() {
            return listOfNotNull(
                discoveryCommand,
                verificationCommand,
                validateWitnessCommand,
                compiledOxstsCommand,
            ) + navigationCommands
        }
}

private val oxstsNavigationCommands = listOf(
    "oxsts.redefinable.redefined.navigate",
    "oxsts.redefinable.redefiners.navigate",
)

object FlavorRegistry {
    val flavors = listOf(
        Flavor(
            id = "oxsts",
            displayName = "OXSTS",
            description = "Semantifyr's language for semantics",
            fileName = "snippet.oxsts",
            language = Language.Oxsts,
            workspaceLayout = WorkspaceLayout.SingleFile,
            verificationCommand = "oxsts.case.verify",
            validateWitnessCommand = "oxsts.case.validateWitness",
            discoveryCommand = "oxsts.case.discover",
            navigationCommands = oxstsNavigationCommands,
        ),
        Flavor(
            id = "oxsts-with-gamma-library",
            displayName = "OXSTS with the Gamma library",
            description = "Gamma's semantics",
            fileName = "snippet.oxsts",
            language = Language.Oxsts,
            workspaceLayout = WorkspaceLayout.WithLibrary(
                libraryRelativePath = Path.of("gamma"),
                workspaceTargetName = "Library",
            ),
            verificationCommand = "oxsts.case.verify",
            validateWitnessCommand = "oxsts.case.validateWitness",
            discoveryCommand = "oxsts.case.discover",
            navigationCommands = oxstsNavigationCommands,
        ),
        Flavor(
            id = "oxsts-with-sysmlv2-library",
            displayName = "OXSTS with the SysML v2 library",
            description = "SysML v2's semantics",
            fileName = "snippet.oxsts",
            language = Language.Oxsts,
            workspaceLayout = WorkspaceLayout.WithLibrary(
                libraryRelativePath = Path.of("sysmlv2"),
                workspaceTargetName = "Library",
            ),
            verificationCommand = "oxsts.case.verify",
            validateWitnessCommand = "oxsts.case.validateWitness",
            discoveryCommand = "oxsts.case.discover",
            navigationCommands = oxstsNavigationCommands,
            editorLanguageIds = listOf(
                Language.Oxsts.id,
                "sysml",
            ),
        ),
        Flavor(
            id = "gamma",
            displayName = "Gamma",
            description = "The Gamma Statechart Composition Framework",
            fileName = "snippet.gamma",
            language = Language.Gamma,
            workspaceLayout = WorkspaceLayout.WithLibrary(
                libraryRelativePath = Path.of("gamma"),
                workspaceTargetName = "Library",
            ),
            verificationCommand = "gamma.case.verify",
            validateWitnessCommand = "gamma.witness.validate",
            validateWitnessByCase = true,
            discoveryCommand = "gamma.case.discover",
            compiledOxstsCommand = "gamma.compiled.oxsts",
        ),
    )

    private val byId = flavors.associateBy {
        it.id
    }

    fun get(id: String): Flavor? {
        return byId[id]
    }
}
