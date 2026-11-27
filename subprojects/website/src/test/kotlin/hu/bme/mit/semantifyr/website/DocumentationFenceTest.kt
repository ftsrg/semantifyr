/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.diagnostics.Diagnostic
import org.eclipse.xtext.diagnostics.Severity
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import org.eclipse.xtext.validation.IResourceValidator
import org.eclipse.xtext.validation.Issue
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path
import kotlin.io.path.readText

@InjectWithOxsts
class DocumentationFenceTest {

    @Serializable
    data class Manifest(
        val pages: List<Page>,
    )

    @Serializable
    data class Page(
        val page: String,
        val fences: List<Fence>,
    )

    @Serializable
    data class Fence(
        val file: String,
        val line: Int,
        val pageLines: List<Int>,
        val continued: Boolean,
        val issue: String?,
    )

    companion object {

        private val fencesDirectory = Path("build/fences")

        private val packageDeclaration = Regex("^\\s*package\\s", RegexOption.MULTILINE)

        @JvmStatic
        fun fencesValidate(): List<Arguments> {
            val manifest = Json.decodeFromString<Manifest>(fencesDirectory.resolve("manifest.json").readText())
            return manifest.pages.flatMap { page ->
                page.fences.map { fence ->
                    Arguments.of(Named.of("${page.page}:${fence.line}", fence), page)
                }
            }
        }

        private fun declaresPackage(fence: Fence): Boolean {
            return packageDeclaration.containsMatchIn(fencesDirectory.resolve(fence.file).readText())
        }

        private fun locationOf(
            page: Page,
            fence: Fence,
            issue: Issue,
        ): String {
            val index = issue.lineNumber - 1
            val pageLine = if (index >= 0 && index < fence.pageLines.size) {
                fence.pageLines[index]
            } else {
                fence.line
            }
            return "${page.page}:$pageLine"
        }
    }

    @Inject
    private lateinit var loader: SemantifyrLoader

    @Inject
    private lateinit var resourceValidator: IResourceValidator

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun fencesValidate(fence: Fence, page: Page) {
        val context = loader.startContext()
        if (!fence.continued) {
            val siblings = page.fences.filter {
                it != fence && !it.continued && it.issue == null && declaresPackage(it)
            }
            for (sibling in siblings) {
                context.loadModel(fencesDirectory.resolve(sibling.file))
            }
        }
        context.loadModel(fencesDirectory.resolve(fence.file))
        EcoreUtil2.resolveAll(context.resourceSet)
        val issues = resourceValidator.validate(context.modelResources.last(), CheckMode.ALL, CancelIndicator.NullImpl).filter {
            it.severity == Severity.ERROR || it.severity == Severity.WARNING
        }
        val codes = issues.map {
            it.code
        }.distinct()
        val described = issues.map {
            "${locationOf(page, fence, it)} ${it.code} ${it.message}"
        }

        val location = "${page.page}:${fence.line}"
        when (val issue = fence.issue) {
            null -> {
                assertThat(described)
                    .describedAs("issues of the oxsts fence at %s", location)
                    .isEmpty()
            }
            "syntax" -> {
                assertThat(codes)
                    .describedAs("the issue codes of the oxsts fence at %s: %s", location, described)
                    .contains(Diagnostic.SYNTAX_DIAGNOSTIC)
            }
            else -> {
                assertThat(codes)
                    .describedAs("the issue codes of the oxsts fence at %s: %s", location, described)
                    .containsExactly(issue)
            }
        }
    }
}
