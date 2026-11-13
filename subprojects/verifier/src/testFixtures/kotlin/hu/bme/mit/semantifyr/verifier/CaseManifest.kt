/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

@Serializable
enum class ManifestVerdict {
    Passed,
    Failed,
}

@Serializable
enum class ManifestTier {
    Smoke,
    Integration,
    Nightly,
}

@Serializable
data class ManifestCase(
    val name: String,
    val verdict: ManifestVerdict,
    val tier: ManifestTier? = null,
) {
    val expectedVerdict: VerificationVerdict
        get() {
            return when (verdict) {
                ManifestVerdict.Passed -> VerificationVerdict.Passed
                ManifestVerdict.Failed -> VerificationVerdict.Failed
            }
        }
}

@Serializable
data class ManifestModel(
    val file: String,
    val cases: List<ManifestCase>,
)

// the expected verdicts of the models beside it, in the shape the corpora share; the SysML v2 corpus adds keys of its own
@Serializable
data class CaseManifest(
    val models: List<ManifestModel>,
) {
    fun expectedVerdict(file: String, caseName: String): VerificationVerdict {
        val model = models.singleOrNull {
            it.file == file
        } ?: error("The manifest lists no $file")
        val case = model.cases.singleOrNull {
            it.name == caseName
        } ?: error("The manifest lists no case $caseName of $file")
        return case.expectedVerdict
    }

    companion object {
        fun load(directory: Path): CaseManifest {
            return Json.decodeFromString(directory.resolve("manifest.json").readText())
        }
    }
}
