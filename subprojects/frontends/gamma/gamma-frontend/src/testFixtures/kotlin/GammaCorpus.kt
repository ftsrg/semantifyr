/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.testing

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

@Serializable
enum class GammaCorpusVerdict {
    Passed,
    Failed,
    Unknown,
}

@Serializable
enum class GammaCorpusTier {
    Smoke,
    Integration,
    Nightly,
}

@Serializable
data class GammaCorpusCase(
    val name: String,
    val verdict: GammaCorpusVerdict,
    val tier: GammaCorpusTier,
) {
    val expectedVerdict: VerificationVerdict
        get() {
            return when (verdict) {
                GammaCorpusVerdict.Passed -> VerificationVerdict.Passed
                GammaCorpusVerdict.Failed -> VerificationVerdict.Failed
                GammaCorpusVerdict.Unknown -> error("Corpus case $name has no expected verdict")
            }
        }
}

@Serializable
data class GammaCorpusModel(
    val file: String,
    val cases: List<GammaCorpusCase>,
)

@Serializable
data class GammaCorpusManifest(
    val models: List<GammaCorpusModel>,
)

data class GammaCorpusEntry(
    val directory: Path,
    val model: GammaCorpusModel,
    val case: GammaCorpusCase,
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
    private val json = Json {
        ignoreUnknownKeys = true
    }

    val modelsRoot: Path = Path("..", "models")

    fun manifestDirectories(root: Path = modelsRoot): List<Path> {
        return Files.list(root).use { directories ->
            directories.filter {
                it.resolve(MANIFEST_FILE).exists()
            }.sorted().toList()
        }
    }

    fun load(directory: Path): List<GammaCorpusEntry> {
        val manifest = json.decodeFromString<GammaCorpusManifest>(directory.resolve(MANIFEST_FILE).readText())
        return manifest.models.flatMap { model ->
            model.cases.map {
                GammaCorpusEntry(directory, model, it)
            }
        }
    }

    fun entries(root: Path = modelsRoot): List<GammaCorpusEntry> {
        return manifestDirectories(root).flatMap {
            load(it)
        }
    }

    private const val MANIFEST_FILE = "manifest.json"
}
