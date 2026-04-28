/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.EcoreUtil2
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.nio.file.Files

@InjectWithOxsts
class TrivialVerdictTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var parseHelper: OxstsPackageParseHelper

    @Test
    suspend fun `short-circuits to Passed when property simplifies to AG true`() {
        assertTriviallyDecided(
            classToVerify = "TrivialPass",
            source = """
                package trivial::tests
                @VerificationCase
                class TrivialPass {
                    prop { return AG true }
                }
            """.trimIndent(),
            expected = VerificationVerdict.Passed,
        )
    }

    @Test
    suspend fun `short-circuits to Failed when property simplifies to AG false`() {
        assertTriviallyDecided(
            classToVerify = "TrivialFailAG",
            source = """
                package trivial::tests
                @VerificationCase
                class TrivialFailAG {
                    prop { return AG false }
                }
            """.trimIndent(),
            expected = VerificationVerdict.Failed,
        )
    }

    @Test
    suspend fun `short-circuits to Passed when property simplifies to EF true`() {
        assertTriviallyDecided(
            classToVerify = "TrivialPassEF",
            source = """
                package trivial::tests
                @VerificationCase
                class TrivialPassEF {
                    prop { return EF true }
                }
            """.trimIndent(),
            expected = VerificationVerdict.Passed,
        )
    }

    @Test
    suspend fun `short-circuits to Failed when property simplifies to EF false`() {
        assertTriviallyDecided(
            classToVerify = "TrivialFailEF",
            source = """
                package trivial::tests
                @VerificationCase
                class TrivialFailEF {
                    prop { return EF false }
                }
            """.trimIndent(),
            expected = VerificationVerdict.Failed,
        )
    }

    @Test
    suspend fun `dispatches to portfolio when model has a variable`() {
        assertDispatchedToPortfolio(
            classToVerify = "DispatchOnVar",
            source = """
                package trivial::tests
                @VerificationCase
                class DispatchOnVar {
                    var x: int := 0
                    prop { return AG (x >= 0) }
                }
            """.trimIndent(),
        )
    }

    @Test
    suspend fun `dispatches to portfolio when init is non-empty`() {
        assertDispatchedToPortfolio(
            classToVerify = "DispatchOnInit",
            source = """
                package trivial::tests
                @VerificationCase
                class DispatchOnInit {
                    var x: int := 0
                    redefine init { x := 1 }
                    prop { return AG (x >= 0) }
                }
            """.trimIndent(),
        )
    }

    @Test
    suspend fun `dispatches to portfolio when tran is non-empty`() {
        assertDispatchedToPortfolio(
            classToVerify = "DispatchOnTran",
            source = """
                package trivial::tests
                @VerificationCase
                class DispatchOnTran {
                    var x: int := 0
                    redefine tran { x := x + 1 }
                    prop { return AG (x >= 0) }
                }
            """.trimIndent(),
        )
    }

    private suspend fun assertTriviallyDecided(
        classToVerify: String,
        source: String,
        expected: VerificationVerdict,
    ) {
        val portfolio = ErroringPortfolio()
        val result = runVerifier(classToVerify, source, portfolio)
        assertThat(result.verdict).isEqualTo(expected)
        assertThat(portfolio.invocations.get())
            .`as`("portfolio must not be invoked when the model is fully optimized away")
            .isEqualTo(0)
        assertThat(result.metadata.backendId).isEqualTo("trivial")
    }

    private suspend fun assertDispatchedToPortfolio(
        classToVerify: String,
        source: String,
    ) {
        val portfolio = ErroringPortfolio()
        runVerifier(classToVerify, source, portfolio)
        assertThat(portfolio.invocations.get())
            .`as`("portfolio must be invoked when the trivial shortcircuit does not apply")
            .isEqualTo(1)
    }

    private suspend fun runVerifier(
        classToVerify: String,
        source: String,
        portfolio: ErroringPortfolio,
    ): VerificationResult {
        val parsed = parseHelper.parse(source)
        val classDecl = EcoreUtil2
            .eAllOfType(parsed.oxstsPackage, ClassDeclaration::class.java)
            .single { it.name == classToVerify }
        val case = VerificationCase(
            classDeclaration = classDecl,
            qualifiedName = "trivial::tests::$classToVerify",
        )
        return buildVerifier(portfolio).use { it.verify(case) }
    }

    private fun buildVerifier(portfolio: ErroringPortfolio): SemantifyrVerifier {
        return SemantifyrVerifier.builder()
            .injector(injector)
            .context(mock<SemantifyrModelContext>())
            .portfolio(portfolio)
            .artifacts(ArtifactConfig.NONE)
            .outputDirectory(Files.createTempDirectory("trivial-verdict-test-"))
            .optimization(OptimizationConfig.NONE)
            .build()
    }
}
