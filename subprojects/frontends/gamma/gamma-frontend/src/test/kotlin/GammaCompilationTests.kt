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
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

@InjectWithGamma
class GammaCompilationTests {

    @Inject
    private lateinit var gammaReader: GammaReader

    @Inject
    private lateinit var gammaCompiler: GammaCompiler

    @Inject
    private lateinit var gammaDiscoverer: GammaVerificationCaseDiscoverer

    private val simpleGammaPath = Path("build", "test-models", "Simple.gamma")

    @Test
    fun `compile produces an oxsts file with the expected package header`(@TempDir temporaryDirectory: Path) {
        val compiled = compileSimple(temporaryDirectory)

        val text = compiled.readText()
        assertThat(text).startsWith("package _Simple")
    }

    @Test
    fun `compile imports the gamma semantic library`(@TempDir temporaryDirectory: Path) {
        val text = compileSimple(temporaryDirectory).readText()

        assertThat(text).contains(
            "import semantifyr::gamma::expressions",
            "import semantifyr::gamma::variables",
            "import semantifyr::gamma::statecharts",
            "import semantifyr::gamma::components",
            "import semantifyr::gamma::triggers",
            "import semantifyr::gamma::actions",
            "import semantifyr::gamma::events",
            "import semantifyr::gamma::ports",
            "import semantifyr::gamma::verification",
        )
    }

    @Test
    fun `compile emits each statechart as an OXSTS Statechart class`(@TempDir temporaryDirectory: Path) {
        val text = compileSimple(temporaryDirectory).readText()

        assertThat(text).contains("class _LeaderStatechart : Statechart")
        assertThat(text).contains("class _WorkerStatechart : Statechart")
    }

    @Test
    fun `compile emits the sync component with channels and bindings`(@TempDir temporaryDirectory: Path) {
        val text = compileSimple(temporaryDirectory).readText()

        assertThat(text).contains("class _System : SyncComponent")
        assertThat(text).contains("contains _leader: _Simple::_LeaderStatechart subsets components")
        assertThat(text).contains("contains _worker: _Simple::_WorkerStatechart subsets components")
        assertThat(text).contains("subsets channels")
    }

    @Test
    fun `compile emits each verification case with the VerificationCase annotation`(@TempDir temporaryDirectory: Path) {
        val text = compileSimple(temporaryDirectory).readText()

        val caseNames = listOf(
            "LeaderStatechartIdleReachable",
            "LeaderStatechartOperationalReachable",
            "LeaderStatechartStoppedReachable",
            "LeaderStatechartUnreachable",
            "WorkerStatechartIdleReachable",
            "WorkerStatechartOperationalReachable",
            "WorkerStatechartStoppedReachable",
            "WorkerStatechartUnreachable",
        )
        for (name in caseNames) {
            assertThat(text).contains("@VerificationCase")
            assertThat(text).contains("class _$name : GammaVerificationCase")
        }
    }

    @Test
    fun `compile creates parent directories when the target does not exist`(@TempDir temporaryDirectory: Path) {
        val gammaModel = gammaReader.readGammaFile(simpleGammaPath.toFile())
        val nested = temporaryDirectory.resolve("nested").resolve("dirs").resolve("model.oxsts")

        assertThat(Files.exists(nested.parent)).isFalse()

        gammaCompiler.compile(gammaModel, nested)

        assertThat(Files.isRegularFile(nested)).isTrue()
        assertThat(nested.readText()).contains("package _Simple")
    }

    @Test
    fun `discover returns a verification case for every declaration`() {
        val gammaModel = gammaReader.readGammaFile(simpleGammaPath.toFile())

        val cases = gammaDiscoverer.discover(gammaModel)

        assertThat(cases).extracting<String> { it.qualifiedName }
            .contains(
                "Simple.LeaderStatechartIdleReachable",
                "Simple.LeaderStatechartOperationalReachable",
                "Simple.LeaderStatechartStoppedReachable",
                "Simple.LeaderStatechartUnreachable",
                "Simple.WorkerStatechartIdleReachable",
                "Simple.WorkerStatechartOperationalReachable",
                "Simple.WorkerStatechartStoppedReachable",
                "Simple.WorkerStatechartUnreachable",
            )
    }

    @Test
    suspend fun `a case of a model importing another package compiles with that package and reaches the verifier`(@TempDir temporaryDirectory: Path) {
        val workspace = temporaryDirectory.resolve("workspace").createDirectories()
        workspace.resolve("Control.gamma").writeText(
            """
                package library::control
                interface Control {
                    in event start
                }
            """.trimIndent(),
        )
        workspace.resolve("Machine.gcd").writeText(
            """
                package test::machine
                import library::control
                statechart Machine [
                    port Control : provides Control
                ] {
                    region Main {
                        initial MainInitial
                        state Idle
                        state Busy
                    }
                    transition from MainInitial to Idle
                    transition from Idle to Busy when Control.start
                }
                verification case BusyReachable of Machine {
                    EF state Main.Busy
                }
            """.trimIndent(),
        )
        val helper = GammaFrontendTestHelper()
        val frontend = helper.buildFrontend(
            portfolio = SingleBackendPortfolio(FakeBackend.verdict("fake", VerificationVerdict.Passed), FakeConfig()),
            outputDirectory = temporaryDirectory.resolve("artifacts"),
        )

        val gammaVerificationCase = helper.discover(workspace.resolve("Machine.gcd")).single()

        helper.checkVerificationCase(frontend, gammaVerificationCase)

        val caseDirectory = temporaryDirectory.resolve("artifacts").resolve(gammaVerificationCase.qualifiedName)
        assertThat(caseDirectory.resolve("model.oxsts").readText()).contains("import _library::_control")
        assertThat(caseDirectory.resolve("imports").resolve("Control.oxsts").readText()).startsWith("package _library::_control")
    }

    private fun compileSimple(temporaryDirectory: Path): Path {
        val gammaModel = gammaReader.readGammaFile(simpleGammaPath.toFile())
        val target = temporaryDirectory.resolve("model.oxsts")
        gammaCompiler.compile(gammaModel, target)
        return target
    }

}
