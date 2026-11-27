/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.testing

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Variant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

@Serializable
enum class CorpusVerdict {
    Passed,
    Failed,
    Unknown,
}

@Serializable
enum class CorpusTier {
    Integration,
    Nightly,
}

@Serializable
data class CorpusCase(
    val name: String,
    val verdict: CorpusVerdict,
    val tier: CorpusTier,
    val falsification: Boolean = false,
) {
    val expectedVerdict: VerificationVerdict
        get() {
            return when (verdict) {
                CorpusVerdict.Passed -> VerificationVerdict.Passed
                CorpusVerdict.Failed -> VerificationVerdict.Failed
                CorpusVerdict.Unknown -> error("Corpus case $name has no expected verdict")
            }
        }
}

@Serializable
data class CorpusModel(
    val file: String,
    val variant: SysMLv2Variant = SysMLv2Variant.Default,
    val conformance: Boolean = false,
    val cases: List<CorpusCase>,
)

data class CorpusEntry(
    val directory: Path,
    val model: CorpusModel,
    val case: CorpusCase,
) {
    val modelPath: Path
        get() {
            return directory.resolve(model.file)
        }

    override fun toString(): String {
        return "${model.file} - ${case.name}"
    }
}

@Serializable
data class SysMLCorpus(
    val models: List<CorpusModel>,
) {
    companion object {
        fun load(directory: Path): SysMLCorpus {
            return Json.decodeFromString(directory.resolve("manifest.json").readText())
        }

        fun entries(directory: Path): List<CorpusEntry> {
            return load(directory).models.flatMap { model ->
                model.cases.map {
                    CorpusEntry(directory, model, it)
                }
            }
        }
    }
}
