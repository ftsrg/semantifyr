/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.corpuscheck

import hu.bme.mit.semantifyr.cli.common.CaseFailureLine
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.cli.common.VerifyOutcomeLine
import hu.bme.mit.semantifyr.cli.common.jsonLineFormat
import hu.bme.mit.semantifyr.verifier.CaseManifest
import hu.bme.mit.semantifyr.verifier.ManifestCase
import hu.bme.mit.semantifyr.verifier.ManifestModel
import hu.bme.mit.semantifyr.verifier.ManifestTier
import hu.bme.mit.semantifyr.verifier.ManifestVerdict
import kotlinx.serialization.SerializationException
import java.nio.file.Path
import kotlin.time.DurationUnit

class ModelSelection(
    val directory: Path,
    val model: ManifestModel,
    val cases: List<ManifestCase>,
) {
    val path: Path
        get() {
            return directory.resolve(model.file)
        }

    val leavesCasesOut: Boolean
        get() {
            return cases.size < model.cases.size
        }
}

enum class CaseStatus {
    Reached,
    Missed,
    Unanswered,
    Unlisted,
    NoVerdict,
    Unreadable,
    LeftOut,
}

data class CaseCheck(
    val file: String,
    val case: String,
    val status: CaseStatus,
    val detail: String,
) {
    val passing: Boolean
        get() {
            return status == CaseStatus.Reached || status == CaseStatus.LeftOut
        }

    override fun toString(): String {
        val label = when (status) {
            CaseStatus.Reached -> {
                "passing"
            }
            CaseStatus.LeftOut -> {
                "left out"
            }
            else -> {
                "failed"
            }
        }
        return "$label $file - $case: $detail"
    }
}

private sealed interface OutputLine {
    class Answer(
        val case: String,
        val exitCode: Int,
        val detail: String,
    ) : OutputLine

    class Unreadable(
        val line: String,
    ) : OutputLine
}

class CorpusChecker(
    private val launcher: Launcher,
    private val caseNamespace: String?,
) {
    fun select(corpus: Path): List<ModelSelection> {
        return CaseManifest.load(corpus).models.map { model ->
            ModelSelection(
                corpus,
                model,
                model.cases.filter {
                    it.tier != ManifestTier.Nightly
                },
            )
        }.filter {
            it.cases.isNotEmpty()
        }
    }

    fun check(selection: ModelSelection): List<CaseCheck> {
        val variant = selection.model.variant
        if (variant != null) {
            return selection.cases.map {
                CaseCheck(selection.model.file, it.name, CaseStatus.LeftOut, "the model needs the library variant $variant, which the launcher does not offer")
            }
        }
        if (caseNamespace == null && selection.leavesCasesOut) {
            return selection.cases.map {
                CaseCheck(selection.model.file, it.name, CaseStatus.Unanswered, "the manifest leaves cases of the model out, and naming the others needs --case-namespace")
            }
        }
        val judged = selection.cases.filter {
            it.verdict != ManifestVerdict.Unknown
        }
        val unjudged = selection.cases.filter {
            it.verdict == ManifestVerdict.Unknown
        }.map {
            CaseCheck(selection.model.file, it.name, CaseStatus.NoVerdict, "the manifest gives no verdict, which only a nightly case may lack")
        }
        if (judged.isEmpty()) {
            return unjudged
        }
        val caseNames = if (caseNamespace == null) {
            emptyList()
        } else {
            judged.map {
                "$caseNamespace${it.name}"
            }
        }
        val run = launcher.verify(selection.path, caseNames)
        val lines = run.stdout.lines().filter {
            it.isNotBlank()
        }.map {
            outputLineOf(it)
        }
        val answers = lines.filterIsInstance<OutputLine.Answer>()
        val unreadable = lines.filterIsInstance<OutputLine.Unreadable>().map {
            CaseCheck(selection.model.file, it.line, CaseStatus.Unreadable, "the launcher printed a line that is neither an outcome nor a failure")
        }
        val checks = judged.map { case ->
            val answer = answers.firstOrNull {
                simpleName(it.case) == case.name
            }
            val expected = ExitCode.of(case.expectedVerdict)
            when {
                answer == null -> {
                    CaseCheck(selection.model.file, case.name, CaseStatus.Unanswered, "no answer, the launcher exited ${run.exitCode}")
                }
                answer.exitCode == expected.code -> {
                    CaseCheck(selection.model.file, case.name, CaseStatus.Reached, "${case.verdict} as expected${answer.detail}")
                }
                else -> {
                    CaseCheck(selection.model.file, case.name, CaseStatus.Missed, "expected ${case.verdict}, answered ${answerWord(answer.exitCode)}${answer.detail}")
                }
            }
        }
        val unlisted = answers.filter { answer ->
            selection.model.cases.none {
                it.name == simpleName(answer.case)
            }
        }.map {
            CaseCheck(selection.model.file, it.case, CaseStatus.Unlisted, "answered ${answerWord(it.exitCode)}, but the manifest does not list the case")
        }
        return unjudged + checks + unlisted + unreadable
    }

    private fun outputLineOf(line: String): OutputLine {
        val outcome = decodedOrNull<VerifyOutcomeLine>(line)
        if (outcome != null) {
            return OutputLine.Answer(outcome.case, outcome.exitCode, " in ${(outcome.compilationDuration + outcome.verificationDuration).toString(DurationUnit.SECONDS, 1)}")
        }
        val failure = decodedOrNull<CaseFailureLine>(line)
        if (failure != null) {
            return OutputLine.Answer(failure.case, failure.exitCode, ": ${failure.error}")
        }
        return OutputLine.Unreadable(line)
    }

    private inline fun <reified T> decodedOrNull(line: String): T? {
        try {
            return jsonLineFormat.decodeFromString<T>(line)
        } catch (_: SerializationException) {
            return null
        }
    }

    private fun simpleName(qualifiedName: String): String {
        return qualifiedName.substringAfterLast("::").substringAfterLast(".")
    }

    private fun answerWord(exitCode: Int): String {
        val code = ExitCode.entries.firstOrNull {
            it.code == exitCode
        }
        return when (code) {
            ExitCode.Success -> {
                "Passed"
            }
            ExitCode.Failed -> {
                "Failed"
            }
            ExitCode.Undecided -> {
                "Inconclusive or NotSupported"
            }
            ExitCode.Timeout -> {
                "Timeout"
            }
            ExitCode.Errored -> {
                "Errored"
            }
            ExitCode.Compilation -> {
                "a compilation error"
            }
            else -> {
                "exit code $exitCode"
            }
        }
    }
}
