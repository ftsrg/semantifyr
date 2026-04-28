/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import java.nio.file.Path

sealed class WorkspaceLayout {
    data object SingleFile : WorkspaceLayout()
    data class WithLibrary(val librarySourceRelativePath: Path) : WorkspaceLayout()
}

data class Flavor(
    val id: String,
    val displayName: String,
    val binaryRelativePath: Path,
    val fileName: String,
    val languageId: String,
    val workspaceLayout: WorkspaceLayout,
    val verificationCommand: String?,
    val discoveryCommand: String? = null,
    val peekCompiledOutput: Boolean = false,
)

object FlavorRegistry {
    val flavors = listOf(
        Flavor(
            id = "oxsts",
            displayName = "Semantifyr",
            binaryRelativePath = Path.of("oxsts.lang.ide", "bin", "oxsts.lang.ide"),
            fileName = "snippet.oxsts",
            languageId = "oxsts",
            workspaceLayout = WorkspaceLayout.SingleFile,
            verificationCommand = "oxsts.case.verify",
            discoveryCommand = "oxsts.case.discover",
        ),
        Flavor(
            id = "oxsts-with-gamma-library",
            displayName = "Semantifyr with Gamma library",
            binaryRelativePath = Path.of("oxsts.lang.ide", "bin", "oxsts.lang.ide"),
            fileName = "snippet.oxsts",
            languageId = "oxsts",
            workspaceLayout = WorkspaceLayout.WithLibrary(
                librarySourceRelativePath = Path.of("gamma.lang.ide", "Library"),
            ),
            verificationCommand = "oxsts.case.verify",
            discoveryCommand = "oxsts.case.discover",
        ),
        Flavor(
            id = "xsts",
            displayName = "XSTS",
            binaryRelativePath = Path.of("xsts.lang.ide", "bin", "xsts.lang.ide"),
            fileName = "snippet.xsts",
            languageId = "xsts",
            workspaceLayout = WorkspaceLayout.SingleFile,
            verificationCommand = null,
        ),
        Flavor(
            id = "gamma",
            displayName = "Gamma",
            binaryRelativePath = Path.of("gamma.lang.ide", "bin", "gamma.lang.ide"),
            fileName = "snippet.gamma",
            languageId = "gamma",
            workspaceLayout = WorkspaceLayout.WithLibrary(
                librarySourceRelativePath = Path.of("gamma.lang.ide", "Library"),
            ),
            verificationCommand = "gamma.case.verify",
            discoveryCommand = "gamma.case.discover",
            peekCompiledOutput = true,
        ),
    )

    private val byId: Map<String, Flavor> = flavors.associateBy { it.id }

    fun get(id: String): Flavor? = byId[id]
}
