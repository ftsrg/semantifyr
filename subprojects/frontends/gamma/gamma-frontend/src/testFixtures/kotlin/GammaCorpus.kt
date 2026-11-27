/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.testing

import hu.bme.mit.semantifyr.verifier.CaseManifest
import hu.bme.mit.semantifyr.verifier.ManifestCase
import hu.bme.mit.semantifyr.verifier.ManifestModel
import java.nio.file.Path
import kotlin.io.path.Path

data class GammaCorpusEntry(
    val directory: Path,
    val model: ManifestModel,
    val case: ManifestCase,
) {
    val modelPath: Path
        get() {
            return directory.resolve(model.file)
        }

    override fun toString(): String {
        return "${directory.fileName}/${model.file} - ${case.name}"
    }
}

object GammaCorpus {
    val corpus: Path = Path("..", "models", "corpus")

    val examples: Path = Path("..", "models", "examples")

    fun load(directory: Path): List<GammaCorpusEntry> {
        return CaseManifest.load(directory).models.flatMap { model ->
            model.cases.map {
                GammaCorpusEntry(directory, model, it)
            }
        }
    }
}
