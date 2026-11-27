/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig

class SemantifyrCompilerFactory @Inject constructor(
    private val injector: Injector,
) {

    fun create(artifactConfig: ArtifactConfig, optimizationConfig: OptimizationConfig): SemantifyrCompiler {
        return SemantifyrCompiler(injector, artifactConfig, optimizationConfig)
    }

}
