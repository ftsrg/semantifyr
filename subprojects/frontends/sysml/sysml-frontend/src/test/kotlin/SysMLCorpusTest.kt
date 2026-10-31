/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusVerdict
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLCorpus
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class SysMLCorpusTest {

    private val corpus = SysMLCorpus.load()

    private val caseDeclaration = Regex("""^\s*verification\s+def\s+(\w+)""", RegexOption.MULTILINE)
    private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)

    private fun declaredCases(model: Path): List<String> {
        val source = model.readText().replace(blockComment, "")
        return caseDeclaration.findAll(source).map {
            it.groupValues[1]
        }.toList()
    }

    @Test
    fun `the manifest lists every model file that declares cases`() {
        val modelFiles = SysMLv2FrontendTestHelper.MODELS_ROOT.listDirectoryEntries().filter {
            it.extension == "sysml" && declaredCases(it).isNotEmpty()
        }.map {
            it.name
        }

        assertThat(corpus.models.map { it.file }).containsExactlyInAnyOrderElementsOf(modelFiles)
    }

    @Test
    fun `every model lists exactly the cases its file declares`() {
        corpus.models.forEach { model ->
            assertThat(model.cases.map { it.name })
                .describedAs(model.file)
                .containsExactlyInAnyOrderElementsOf(declaredCases(SysMLv2FrontendTestHelper.modelPath(model.file)))
        }
    }

    @Test
    fun `known verdicts follow the Violated suffix`() {
        corpus.entries.filter {
            it.case.verdict != CorpusVerdict.Unknown
        }.forEach {
            val suffixVerdict = if (it.case.name.endsWith("Violated")) {
                CorpusVerdict.Failed
            } else {
                CorpusVerdict.Passed
            }
            assertThat(it.case.verdict).describedAs(it.toString()).isEqualTo(suffixVerdict)
        }
    }

    @Test
    fun `falsification twins are expected to fail`() {
        corpus.entries.filter {
            it.case.falsification
        }.forEach {
            assertThat(it.case.verdict).describedAs(it.toString()).isEqualTo(CorpusVerdict.Failed)
        }
    }

    @Test
    fun `cases without a known verdict are nightly only`() {
        corpus.entries.filter {
            it.case.verdict == CorpusVerdict.Unknown
        }.forEach {
            assertThat(it.case.tier).describedAs(it.toString()).isEqualTo(CorpusTier.Nightly)
        }
    }
}
