/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import java.nio.file.Path

data class CompilationRequest(
    val inlinedOxsts: InlinedOxsts,
    val outputDirectory: Path,
)
