/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.testing

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Frontend
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Variant
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.testing.ExampleLanguage
import hu.bme.mit.semantifyr.portfolios.testing.ServedExamples
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.assertVerdict
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SysMLv2FrontendTestHelper {

    private val logger by loggerFactory()

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val loader = injector.getInstance(SemantifyrLoader::class.java)
    private val verifierTestHelper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

    fun buildFrontend(
        sourcePath: Path,
        portfolio: VerificationPortfolio,
        outputDirectory: Path,
        variant: SysMLv2Variant = SysMLv2Variant.Default,
        timeout: Duration = 2.minutes,
        artifacts: ArtifactConfig = ArtifactConfig.ALL,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
    ): SysMLv2Frontend {
        return SysMLv2Frontend.builder()
            .source(sourcePath)
            .variant(variant)
            .injector(injector)
            .loader(loader)
            .portfolio(portfolio)
            .environment(environment)
            .timeout(timeout)
            .artifacts(artifacts)
            .outputDirectory(outputDirectory)
            .build()
    }

    fun buildFrontend(
        entry: CorpusEntry,
        portfolio: VerificationPortfolio,
        outputDirectory: Path,
        timeout: Duration = 2.minutes,
    ): SysMLv2Frontend {
        return buildFrontend(modelPath(entry.model.file), portfolio, outputDirectory, entry.model.variant, timeout)
    }

    private fun verificationCase(frontend: SysMLv2Frontend, entry: CorpusEntry): VerificationCase {
        return frontend.verificationCases().single {
            it.qualifiedName.substringAfterLast("::") == entry.case.name
        }
    }

    suspend fun checkVerificationCase(
        frontend: SysMLv2Frontend,
        entry: CorpusEntry,
    ) {
        val case = verificationCase(frontend, entry)
        val result = frontend.verify(case)
        assertVerdict(case.qualifiedName, entry.case.expectedVerdict, result)
    }

    // the trace command writes a trace only after SysIDE validates it against the model, so its file is the check
    suspend fun checkSysMLTrace(
        frontend: SysMLv2Frontend,
        entry: CorpusEntry,
    ): Path? {
        val case = verificationCase(frontend, entry)
        val result = frontend.verify(case)
        assertVerdict(case.qualifiedName, entry.case.expectedVerdict, result)
        val tracePath = frontend.sysmlTracePath(case)
        if (result.trace == null) {
            assertThat(tracePath).describedAs("SysML trace of $entry, which has no witness").doesNotExist()
            return null
        }
        assertThat(tracePath).describedAs("SysML trace of $entry").isRegularFile()
        return tracePath
    }

    suspend fun recordVerificationCase(
        frontend: SysMLv2Frontend,
        entry: CorpusEntry,
    ) {
        val result = frontend.verify(verificationCase(frontend, entry))
        logger.info { "$entry: ${result.verdict} after ${result.metrics.totalDuration} (${result.message ?: "no message"})" }
        Assertions.assertNotEquals(VerificationVerdict.Errored, result.verdict) {
            "$entry errored: ${result.message ?: "no message"}"
        }
    }

    suspend fun checkConformance(
        frontend: SysMLv2Frontend,
        entry: CorpusEntry,
        validationPortfolio: VerificationPortfolio? = null,
    ) {
        verifierTestHelper.checkTestModel(
            context = frontend.modelContext,
            case = verificationCase(frontend, entry),
            verificationPortfolio = frontend.portfolio,
            outputDirectory = frontend.outputDirectory,
            validationPortfolio = validationPortfolio,
            timeout = frontend.timeout ?: 30.minutes,
            expectedVerdict = entry.case.expectedVerdict,
        )
    }

    companion object {
        val MODELS_ROOT: Path = Path("build", "test-models")

        private val served by lazy {
            ServedExamples.load().of(ExampleLanguage.Sysml)
        }

        // a served case is verified by the served-case test alone
        fun isServed(entry: CorpusEntry): Boolean {
            return served.any {
                it.path.name == entry.model.file && entry.case.name in it.cases
            }
        }

        val TUTORIAL_ROOT: Path = Path("..", "models", "tutorial")

        val ETC_ROOT: Path = Path("..", "models", "etc")

        val tutorialEntries: List<CorpusEntry>
            get() {
                return SysMLCorpus.load(TUTORIAL_ROOT.resolve("manifest.json")).entries
            }

        fun guardedInitialTransitionEntries(variant: SysMLv2Variant): List<CorpusEntry> {
            return SysMLCorpus.load(ETC_ROOT.resolve("manifest.json")).models.map {
                it.copy(variant = variant)
            }.flatMap { model ->
                model.cases.map {
                    CorpusEntry(model, it)
                }
            }
        }

        // the SysML CLI reads every model next to the source, so the fixture gets a workspace of its own
        fun guardedInitialTransitionModel(workspace: Path): Path {
            val model = SysMLCorpus.load(ETC_ROOT.resolve("manifest.json")).models.single().file
            workspace.createDirectories()
            modelPath("Verification.sysml").copyTo(workspace.resolve("Verification.sysml"), overwrite = true)
            return ETC_ROOT.resolve(model).copyTo(workspace.resolve(model), overwrite = true)
        }

        fun testArtifactRoot(testClass: Class<*>): Path {
            return Path("build", "test-artifacts", testClass.simpleName)
        }

        fun modelPath(name: String): Path {
            return MODELS_ROOT.resolve(name)
        }
    }
}
