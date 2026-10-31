/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.testing

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Variant
import kotlinx.serialization.SerialName
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
    Smoke,
    Integration,
    Nightly,
}

@Serializable
enum class CorpusEngine {
    @SerialName("theta")
    Theta,

    @SerialName("spin")
    Spin,

    @SerialName("nuxmv")
    Nuxmv,

    @SerialName("uppaal")
    Uppaal,
}

@Serializable
data class CorpusCase(
    val name: String,
    val verdict: CorpusVerdict,
    val tier: CorpusTier,
    val engines: List<CorpusEngine> = emptyList(),
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
data class CorpusExample(
    val label: String,
    val description: String,
)

@Serializable
data class CorpusModel(
    val file: String,
    val variant: SysMLv2Variant = SysMLv2Variant.Default,
    val conformance: Boolean = false,
    val example: CorpusExample? = null,
    val cases: List<CorpusCase>,
)

data class CorpusEntry(
    val model: CorpusModel,
    val case: CorpusCase,
) {
    override fun toString(): String {
        return "${model.file} - ${case.name}"
    }
}

@Serializable
data class SysMLCorpus(
    val models: List<CorpusModel>,
) {
    val entries: List<CorpusEntry>
        get() {
            return models.flatMap { model ->
                model.cases.map {
                    CorpusEntry(model, it)
                }
            }
        }

    companion object {
        fun load(path: Path = SysMLv2FrontendTestHelper.modelPath("manifest.json")): SysMLCorpus {
            return Json.decodeFromString(path.readText())
        }
    }
}
