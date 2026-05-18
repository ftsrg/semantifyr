/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import hu.bme.mit.semantifyr.frontends.gamma.GammaCompiler
import hu.bme.mit.semantifyr.frontends.gamma.discovery.GammaVerificationCaseDiscoverer
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
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

    private val simpleGammaPath = Path("build", "test-models", "Simple.gamma")

    @Test
    fun `compile produces an oxsts file with the expected package header`(@TempDir tmp: Path) {
        val compiled = compileSimple(tmp)

        val text = compiled.readText()
        assertThat(text).startsWith("package Simple")
    }

    @Test
    fun `compile imports the gamma semantic library`(@TempDir tmp: Path) {
        val text = compileSimple(tmp).readText()

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
    fun `compile emits each statechart as an OXSTS Statechart class`(@TempDir tmp: Path) {
        val text = compileSimple(tmp).readText()

        assertThat(text).contains("class LeaderStatechart : Statechart")
        assertThat(text).contains("class WorkerStatechart : Statechart")
    }

    @Test
    fun `compile emits the sync component with channels and bindings`(@TempDir tmp: Path) {
        val text = compileSimple(tmp).readText()

        assertThat(text).contains("class System : SyncComponent")
        assertThat(text).contains("contains leader: LeaderStatechart subsets components")
        assertThat(text).contains("contains worker: WorkerStatechart subsets components")
        assertThat(text).contains("subsets channels")
    }

    @Test
    fun `compile emits each verification case with the VerificationCase annotation`(@TempDir tmp: Path) {
        val text = compileSimple(tmp).readText()

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
            assertThat(text).contains("class $name : GammaVerificationCase")
        }
    }

    @Test
    fun `compile creates parent directories when the target does not exist`(@TempDir tmp: Path) {
        val gammaModel = gammaReader.readGammaFile(simpleGammaPath.toFile())
        val nested = tmp.resolve("nested").resolve("dirs").resolve("model.oxsts")

        assertThat(Files.exists(nested.parent)).isFalse()

        gammaCompiler.compile(gammaModel, nested)

        assertThat(Files.isRegularFile(nested)).isTrue()
        assertThat(nested.readText()).contains("package Simple")
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

    private fun compileSimple(tmp: Path): Path {
        val gammaModel = gammaReader.readGammaFile(simpleGammaPath.toFile())
        val target = tmp.resolve("model.oxsts")
        gammaCompiler.compile(gammaModel, target)
        return target
    }

}
