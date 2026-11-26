/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Variant
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusVerdict
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLCorpus
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.testing.ExampleLanguage
import hu.bme.mit.semantifyr.portfolios.testing.ServedExamples
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class SysMLCorpusTest {

    private val corpus = SysMLCorpus.load()

    private val caseDeclaration = Regex("""^\s*verification\s+def\s+(\w+)""", RegexOption.MULTILINE)
    private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)

    private val decisionsPage = Path("build", "decisions-page", "semantic-decisions.mdx")
    private val decisionHeading = Regex("""^### .+ \{#([a-z]+(?:-[a-z]+)*)}$""", RegexOption.MULTILINE)

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
    fun `falsification twins are expected to fail`() {
        corpus.entries.filter {
            it.case.falsification
        }.forEach {
            assertThat(it.case.verdict).describedAs(it.toString()).isEqualTo(CorpusVerdict.Failed)
        }
    }

    @Test
    fun `served models compile without a library variant`() {
        val served = ServedExamples.load().of(ExampleLanguage.Sysml).map {
            it.path.name
        }

        assertThat(served).isNotEmpty()
        corpus.models.filter {
            it.file in served
        }.forEach {
            assertThat(it.variant).describedAs(it.file).isEqualTo(SysMLv2Variant.Default)
        }
    }

    @Test
    fun `every decision a case names is a decision of the decisions page`() {
        val decisions = decisionHeading.findAll(decisionsPage.readText()).map {
            it.groupValues[1]
        }.toSet()

        assertThat(decisions).isNotEmpty()
        corpus.entries.forEach {
            assertThat(decisions).describedAs(it.toString()).containsAll(it.case.decisions)
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
