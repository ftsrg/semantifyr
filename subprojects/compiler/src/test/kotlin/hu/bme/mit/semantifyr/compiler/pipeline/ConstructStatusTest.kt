/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.diagnostics.Severity
import org.eclipse.xtext.resource.XtextSyntaxDiagnostic
import org.eclipse.xtext.validation.CheckMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readLines
import kotlin.io.path.readText

@InjectWithOxsts
class ConstructStatusTest {

    companion object {
        private val testModels = Path("../../oxsts-test-models")

        @JvmStatic
        fun constructs(): List<Arguments> {
            return testModels.resolve("constructs/constructs.tsv").readLines().drop(1).filter {
                it.isNotBlank()
            }.map {
                val columns = it.split("\t")
                check(columns.size == 4) {
                    "Expected four tab-separated columns: $it"
                }
                Arguments.argumentSet(columns[0], columns[1], columns[2], columns[3])
            }
        }
    }

    @TempDir
    lateinit var tempDir: Path

    @Inject
    private lateinit var parseHelper: OxstsPackageParseHelper

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var builtinAnnotationHandler: BuiltinAnnotationHandler

    @ParameterizedTest
    @MethodSource("constructs")
    fun `construct has the outcome the status table claims`(
        outcome: String,
        fixture: String,
        detail: String,
    ) {
        val source = testModels.resolve(fixture).readText()
        when (outcome) {
            "parse" -> assertRejectedByParser(source)
            "reject" -> assertRejectedByValidator(source, detail)
            "pass" -> assertCompilesAsTagged(source, detail, "expect-pass")
            "fail" -> assertCompilesAsTagged(source, detail, "expect-fail")
            else -> error("Unknown outcome '$outcome'")
        }
    }

    private fun assertRejectedByParser(source: String) {
        val parsed = parseHelper.parse(source)
        assertThat(parsed.resourceErrors)
            .`as`("the grammar should reject the construct")
            .anySatisfy {
                assertThat(it).isInstanceOf(XtextSyntaxDiagnostic::class.java)
            }
    }

    private fun assertRejectedByValidator(source: String, code: String) {
        val parsed = parseHelper.parse(source, CheckMode.ALL)
        parsed.assertNoResourceErrors()
        val errors = parsed.issues.filter {
            it.severity == Severity.ERROR
        }
        assertThat(errors)
            .`as`("the validator should reject the construct with $code and nothing else")
            .isNotEmpty
            .allSatisfy {
                assertThat(it.code).isEqualTo(code)
                assertThat(it.lineNumber).isPositive
            }
    }

    private fun assertCompilesAsTagged(
        source: String,
        className: String,
        tag: String,
    ) {
        val parsed = parseHelper.parse(source, CheckMode.ALL)
        parsed.assertNoResourceErrors()
        parsed.assertNoValidationErrors()
        val classDeclaration = EcoreUtil2.eAllOfType(parsed.oxstsPackage, ClassDeclaration::class.java).single {
            it.name == className
        }
        assertThat(builtinAnnotationHandler.isTaggedWith(classDeclaration, tag))
            .`as`("$className should carry @Tag(\"$tag\"), which the semantic conformance suites verify")
            .isTrue()
        SemantifyrCompiler(injector = injector, artifactConfig = ArtifactConfig.NONE).compile(classDeclaration, tempDir)
    }
}
