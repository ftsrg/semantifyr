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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class SysMLCorpusTest {

    companion object {
        @JvmStatic
        fun corpusDirectories(): List<Path> {
            return listOf(
                SysMLv2FrontendTestHelper.EXAMPLES_ROOT,
                SysMLv2FrontendTestHelper.SEMANTICS_ROOT,
                SysMLv2FrontendTestHelper.TESTS_ROOT,
            )
        }
    }

    private val caseDeclaration = Regex("""^\s*verification\s+def\s+(\w+)""", RegexOption.MULTILINE)
    private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)

    private fun declaredCases(model: Path): List<String> {
        val source = model.readText().replace(blockComment, "")
        return caseDeclaration.findAll(source).map {
            it.groupValues[1]
        }.toList()
    }

    @ParameterizedTest
    @MethodSource("corpusDirectories")
    fun `the manifest lists every model file that declares cases`(directory: Path) {
        val modelFiles = directory.listDirectoryEntries().filter {
            it.extension == "sysml" && declaredCases(it).isNotEmpty()
        }.map {
            it.name
        }

        assertThat(
            SysMLCorpus.load(directory).models.map {
                it.file
            },
        ).containsExactlyInAnyOrderElementsOf(modelFiles)
    }

    @Test
    fun `falsification twins are expected to fail`() {
        SysMLv2FrontendTestHelper.corpusEntries.filter {
            it.case.falsification
        }.forEach {
            assertThat(it.case.verdict).describedAs(it.toString()).isEqualTo(CorpusVerdict.Failed)
        }
    }

    @Test
    fun `cases without a known verdict are nightly only`() {
        SysMLv2FrontendTestHelper.corpusEntries.filter {
            it.case.verdict == CorpusVerdict.Unknown
        }.forEach {
            assertThat(it.case.tier).describedAs(it.toString()).isEqualTo(CorpusTier.Nightly)
        }
    }
}
