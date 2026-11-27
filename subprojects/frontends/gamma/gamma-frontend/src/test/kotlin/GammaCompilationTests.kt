/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.frontends.gamma.GammaCompiler
import hu.bme.mit.semantifyr.frontends.gamma.discovery.GammaVerificationCaseDiscoverer
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText

@InjectWithGamma
class GammaCompilationTests {

    @Inject
    private lateinit var gammaReader: GammaReader

    @Inject
    private lateinit var gammaCompiler: GammaCompiler

    @Inject
    private lateinit var gammaDiscoverer: GammaVerificationCaseDiscoverer

    private val smokeModel = Path("..", "models", "smoke", "smoke.gamma")

    private val resources = Path("src", "test", "resources", "GammaCompilationTests")

    @Test
    fun `compile produces an oxsts file with the expected package header`(@TempDir temporaryDirectory: Path) {
        val compiled = compileSmoke(temporaryDirectory)

        val text = compiled.readText()
        assertThat(text).startsWith("package _smoke")
    }

    @Test
    fun `compile creates parent directories when the target does not exist`(@TempDir temporaryDirectory: Path) {
        val gammaModel = gammaReader.readGammaFile(smokeModel.toFile())
        val nested = temporaryDirectory.resolve("nested").resolve("dirs").resolve("model.semantifyr")

        assertThat(Files.exists(nested.parent)).isFalse()

        gammaCompiler.compile(gammaModel, nested)

        assertThat(Files.isRegularFile(nested)).isTrue()
        assertThat(nested.readText()).contains("package _smoke")
    }

    @Test
    fun `discover returns a verification case for every declaration`() {
        val gammaModel = gammaReader.readGammaFile(smokeModel.toFile())

        val cases = gammaDiscoverer.discover(gammaModel)

        assertThat(
            cases.map {
                it.qualifiedName
            },
        ).containsExactlyInAnyOrder(
            "smoke.OnReachable",
            "smoke.OffReachable",
        )
    }

    @Test
    suspend fun `a case of a model importing another package compiles with that package and reaches the verifier`(@TempDir temporaryDirectory: Path) {
        val helper = GammaFrontendTestHelper()
        val frontend = helper.buildFrontend(
            portfolio = SingleBackendPortfolio(FakeBackend.verdict("fake", VerificationVerdict.Passed), FakeConfig()),
            outputDirectory = temporaryDirectory.resolve("artifacts"),
        )

        val gammaVerificationCase = helper.discover(resources.resolve("importing").resolve("Machine.gcd")).single()

        helper.checkVerificationCase(frontend, gammaVerificationCase, VerificationVerdict.Passed)

        val caseDirectory = temporaryDirectory.resolve("artifacts").resolve(gammaVerificationCase.qualifiedName)
        assertThat(caseDirectory.resolve("model.semantifyr").readText()).contains("import _library::_control")
        assertThat(caseDirectory.resolve("imports").resolve("Control.semantifyr").readText()).startsWith("package _library::_control")
    }

    @Test
    fun `no main transition compiled from a timed Gamma model holds an unclosed clock test`(@TempDir temporaryDirectory: Path) {
        val model = resources.resolve("Timed.gamma")
        val helper = GammaFrontendTestHelper()
        val clockTestAnalysis = helper.clockTestAnalysis()

        for (gammaVerificationCase in helper.discover(model)) {
            val inlinedOxsts = helper.compileToOxsts(gammaVerificationCase, temporaryDirectory.resolve(gammaVerificationCase.qualifiedName))

            assertThat(
                inlinedOxsts.variables.filter {
                    clockTestAnalysis.isClock(it)
                },
            ).describedAs(gammaVerificationCase.qualifiedName).hasSize(2)
            assertThat(clockTestAnalysis.unclosedTests(inlinedOxsts.mainTransition)).describedAs(gammaVerificationCase.qualifiedName).isEmpty()
        }
    }

    private fun compileSmoke(temporaryDirectory: Path): Path {
        val gammaModel = gammaReader.readGammaFile(smokeModel.toFile())
        val target = temporaryDirectory.resolve("model.semantifyr")
        gammaCompiler.compile(gammaModel, target)
        return target
    }
}
