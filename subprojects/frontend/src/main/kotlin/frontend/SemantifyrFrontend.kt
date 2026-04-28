/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontend

import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.verification.SemantifyrVerifier
import java.nio.file.Path

abstract class SemantifyrFrontend(
    protected val loader: SemantifyrLoader,
) : AutoCloseable {

    protected fun extractClasspathLibraries(resourcePrefix: String): List<Path> {
        return FrontendLibraryExtractor.extractClasspath(javaClass.classLoader, resourcePrefix)
    }

    protected fun loadCompiledModel(
        modelPath: Path,
        libraryPaths: List<Path> = emptyList(),
    ): SemantifyrModelContext {
        return loader
            .startContext()
            .loadLibraryPaths(libraryPaths)
            .loadModelPaths(listOf(modelPath))
            .buildAndResolve()
    }

    protected fun buildVerifier(
        context: SemantifyrModelContext,
        configure: SemantifyrVerifier.Builder.() -> Unit,
    ): SemantifyrVerifier {
        return SemantifyrVerifier
            .builder()
            .context(context)
            .apply(configure)
            .build()
    }
}
