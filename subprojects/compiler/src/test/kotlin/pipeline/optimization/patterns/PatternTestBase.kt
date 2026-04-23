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
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PatternOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.verifyInjectedDependenciesAreBound
import hu.bme.mit.semantifyr.compiler.pipeline.utils.normalizedFixtureSource
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.serializer.ISerializer
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock

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

    @BeforeEach
    fun verifyInjectedDependencies() {
        verifyInjectedDependenciesAreBound(this)
    }

    protected fun assertPatternTransforms(
        pattern: OptimizationPattern,
        input: String,
        expected: String,
    ) {
        val inputModel = parseHelper.parse(input.normalizedFixtureSource())
        val expectedModel = parseHelper.parse(expected.normalizedFixtureSource())

        runPattern(pattern, inputModel)

        if (!EcoreUtil.equals(inputModel, expectedModel)) {
            val actualText = serializer.serializeFormatted(inputModel)
            val expectedText = serializer.serializeFormatted(expectedModel)
            Assertions.assertThat(actualText)
                .describedAs(
                    "Pattern ${pattern::class.simpleName} did not rewrite the input to the expected form (structural EcoreUtil.equals was false; serialized forms follow)",
                )
                .isEqualTo(expectedText)
            throw AssertionError(
                "Pattern ${pattern::class.simpleName} produced an AST that is not structurally equal to the expected AST, even though the serialized forms match. The ASTs likely differ in cross-reference targets or non-syntactic attributes.",
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
        val optimizer = PatternOptimizer(
            patterns = listOf(pattern),
            pass = CompilationPass.ExpressionSimplification,
            artifactManager = artifacts,
        )
        optimizer.optimize(inputModel)
    }
}
