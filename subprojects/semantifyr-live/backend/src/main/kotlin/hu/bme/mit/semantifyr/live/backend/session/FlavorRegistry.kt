/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import java.nio.file.Path

enum class WorkspaceLayout {
    SingleFile,
    // Future: WithLibrary — single user file plus a read-only stdlib subdir (for sysmlv2 etc.)
}

data class Flavor(
    val id: String,
    val displayName: String,
    val binaryRelativePath: Path,
    val fileName: String,
    val languageId: String,
    val workspaceLayout: WorkspaceLayout,
    val verifyCommand: String?,
)

object FlavorRegistry {
    val flavors = listOf(
        Flavor(
            id = "oxsts",
            displayName = "OxSTS",
            binaryRelativePath = Path.of("oxsts.lang.ide", "bin", "oxsts.lang.ide"),
            fileName = "snippet.oxsts",
            languageId = "oxsts",
            workspaceLayout = WorkspaceLayout.SingleFile,
            verifyCommand = "oxsts.case.verify",
        ),
        Flavor(
            id = "xsts",
            displayName = "XSTS",
            binaryRelativePath = Path.of("xsts.lang.ide", "bin", "xsts.lang.ide"),
            fileName = "snippet.xsts",
            languageId = "xsts",
            workspaceLayout = WorkspaceLayout.SingleFile,
            verifyCommand = null,
        ),
        Flavor(
            id = "gamma",
            displayName = "Gamma",
            binaryRelativePath = Path.of("gamma.lang.ide", "bin", "gamma.lang.ide"),
            fileName = "snippet.gamma",
            languageId = "gamma",
            workspaceLayout = WorkspaceLayout.SingleFile,
            verifyCommand = null,
        ),
    )

    private val byId: Map<String, Flavor> = flavors.associateBy { it.id }

    fun get(id: String): Flavor? = byId[id]
}
