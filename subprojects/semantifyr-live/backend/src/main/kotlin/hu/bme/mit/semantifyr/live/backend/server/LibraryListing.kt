/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.FlavorRegistry
import hu.bme.mit.semantifyr.live.backend.WorkspaceLayout
import hu.bme.mit.semantifyr.live.backend.data.LibrariesResponse
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServiceRegistry
import java.nio.file.Files
import java.nio.file.Path

private val MODEL_EXTENSIONS = listOf(
    ".oxsts",
    ".sysml",
    ".kerml",
)

// the directory the SysML worker loads its standard library from, under the web root
private val SYSML_LIBRARY_DIRECTORY = Path.of("sysml-worker", "library")

@Singleton
class LibraryListing @Inject constructor(
    private val config: BackendConfig,
    private val languageServiceRegistry: LanguageServiceRegistry,
) {

    val libraries by lazy {
        val builtinRoot = languageServiceRegistry.forFlavor(FlavorRegistry.flavors.first()).builtinLibrary.extractedLibraryPath
        val semanticRoot = config.sessionManager.semanticLibrariesPath
        LibrariesResponse(
            builtin = modelFilesUnder(builtinRoot),
            semantic = FlavorRegistry.flavors.mapNotNull { flavor ->
                val layout = flavor.workspaceLayout as? WorkspaceLayout.WithLibrary
                if (layout == null || semanticRoot == null) {
                    null
                } else {
                    flavor.id to modelFilesUnder(semanticRoot.resolve(layout.libraryRelativePath))
                }
            }.toMap(),
            sysml = config.server.webRootPath?.let {
                modelFilesUnder(it.resolve(SYSML_LIBRARY_DIRECTORY))
            }.orEmpty(),
        )
    }

    private fun modelFilesUnder(root: Path): List<String> {
        if (!Files.isDirectory(root)) {
            return emptyList()
        }
        return Files.walk(root).use { paths ->
            paths.filter {
                Files.isRegularFile(it)
            }.map {
                root.relativize(it).joinToString("/")
            }.filter { path ->
                MODEL_EXTENSIONS.any {
                    path.endsWith(it)
                }
            }.sorted().toList()
        }
    }
}
