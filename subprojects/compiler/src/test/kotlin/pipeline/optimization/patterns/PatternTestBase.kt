/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.WorklistOptimizer
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import org.mockito.kotlin.mock
import java.io.StringWriter

/**
 * Base class for pattern-level optimization tests.
 *
 * Fixtures are written in the inlined-oxsts form the optimizer actually runs on
 * (the same form the compiler serializes at each pass-step), so the "input" and
 * "expected" snippets describe the IR shape precisely:
 *
 *     assertPatternTransforms(
 *         pattern = IdempotentBooleanPattern(),
 *         input = """
 *             inlined oxsts of semantifyr::Anything
 *             var x : bool := false
 *             init { }
 *             tran { }
 *             prop { AG (x && x) }
 *         """,
 *         expected = """
 *             inlined oxsts of semantifyr::Anything
 *             var x : bool := false
 *             init { }
 *             tran { }
 *             prop { AG x }
 *         """,
 *     )
 *
 * Tests only use top-level [hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts.variables] (no root feature, no nested
 * classes); [semantifyr::Anything] is a builtin stub that satisfies the required
 * [hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts.classDeclaration] cross-reference.
 */
@InjectWithOxsts
abstract class PatternTestBase {

    @Inject
    protected lateinit var parseHelper: InlinedOxstsParseHelper

    @Inject
    protected lateinit var serializer: ISerializer

    protected fun assertPatternTransforms(
        pattern: OptimizationPattern,
        input: String,
        expected: String,
    ) {
        val inputModel = parseHelper.parse(input.trimIndent())
        val expectedModel = parseHelper.parse(expected.trimIndent())

        runPattern(pattern, inputModel)

        if (!EcoreUtil.equals(inputModel, expectedModel)) {
            val actualText = normalize(serialize(inputModel))
            val expectedText = normalize(serialize(expectedModel))
            Assertions.assertThat(actualText)
                .describedAs(
                    "Pattern ${pattern::class.simpleName} did not rewrite the input to the expected form " +
                        "(structural EcoreUtil.equals was false; serialized forms follow)",
                )
                .isEqualTo(expectedText)
            throw AssertionError(
                "Pattern ${pattern::class.simpleName} produced an AST that is not structurally equal " +
                    "to the expected AST, even though the serialized forms match. The ASTs likely differ " +
                    "in cross-reference targets or non-syntactic attributes.",
            )
        }
    }

    protected fun assertPatternDoesNotMatch(
        pattern: OptimizationPattern,
        input: String,
    ) {
        assertPatternTransforms(pattern, input, input)
    }

    private fun runPattern(pattern: OptimizationPattern, inputModel: InlinedOxsts) {
        val artifacts: CompilationArtifactManager = mock()
        val optimizer = WorklistOptimizer(
            patterns = listOf(pattern),
            pass = CompilationPass.ExpressionSimplification,
            artifactManager = artifacts,
        )
        optimizer.optimize(inputModel)
    }

    private fun serialize(model: InlinedOxsts): String {
        val writer = StringWriter()
        serializer.serialize(model, writer, SaveOptions.defaultOptions())
        return writer.toString()
    }

    private fun normalize(text: String): String {
        return text.replace(WHITESPACE_RUN, " ").trim()
    }

    private companion object {
        private val WHITESPACE_RUN = Regex("\\s+")
    }
}
