/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.testing

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Frontend
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Variant
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
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
    ): SysMLv2Frontend {
        return SysMLv2Frontend.builder()
            .source(sourcePath)
            .variant(variant)
            .injector(injector)
            .loader(loader)
            .portfolio(portfolio)
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
        Assertions.assertEquals(
            entry.case.expectedVerdict,
            result.verdict,
            "Expected ${case.qualifiedName} verdict ${entry.case.expectedVerdict}, got ${result.verdict} (${result.message ?: "no message"})",
        )
    }

    // the trace command writes a trace only after SysIDE validates it against the model, so its file is the check
    suspend fun checkSysMLTrace(
        frontend: SysMLv2Frontend,
        entry: CorpusEntry,
    ): Path? {
        val case = verificationCase(frontend, entry)
        val result = frontend.verify(case)
        Assertions.assertEquals(
            entry.case.expectedVerdict,
            result.verdict,
            "Expected ${case.qualifiedName} verdict ${entry.case.expectedVerdict}, got ${result.verdict} (${result.message ?: "no message"})",
        )
        val tracePath = frontend.sysmlTracePath(case)
        if (result.trace == null) {
            assertThat(tracePath).describedAs("SysML trace of $entry, which has no witness").doesNotExist()
            return null
        }
        assertThat(tracePath).describedAs("SysML trace of $entry").isRegularFile()
        return tracePath
    }

    // the tutorial models are outside the corpus, so their cases come from discovery and the Violated suffix
    fun tutorialEntries(
        frontend: SysMLv2Frontend,
        source: Path,
    ): List<CorpusEntry> {
        val model = CorpusModel(
            file = source.name,
            cases = frontend.verificationCases().map {
                val name = it.qualifiedName.substringAfterLast("::")
                val verdict = if (name.endsWith("Violated")) CorpusVerdict.Failed else CorpusVerdict.Passed
                CorpusCase(name, verdict, CorpusTier.Integration)
            },
        )
        return model.cases.map {
            CorpusEntry(model, it)
        }
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

        val TUTORIAL_MODELS: List<Path>
            get() {
                return Path("..", "models", "tutorial").listDirectoryEntries("*.sysml").sorted()
            }

        private const val GUARDED_INITIAL_TRANSITION_MODEL = "guarded_initial_transition.sysml"

        // the model is a library fixture outside the corpus, so its cases are listed here
        private val GUARDED_INITIAL_TRANSITION_CASES = listOf(
            "GuardedInitialTransitionEntersLowTestCase",
            "GuardedInitialTransitionNeverEntersHighTestCase",
            "GuardedNestedInitialTransitionEntersRightTestCase",
            "GuardedNestedInitialTransitionNeverEntersLeftTestCase",
        )

        fun guardedInitialTransitionEntries(variant: SysMLv2Variant): List<CorpusEntry> {
            val model = CorpusModel(
                file = GUARDED_INITIAL_TRANSITION_MODEL,
                variant = variant,
                cases = GUARDED_INITIAL_TRANSITION_CASES.map {
                    CorpusCase(it, CorpusVerdict.Passed, CorpusTier.Integration)
                },
            )
            return model.cases.map {
                CorpusEntry(model, it)
            }
        }

        // the SysML CLI reads every model next to the source, so the fixture gets a workspace of its own
        fun guardedInitialTransitionModel(workspace: Path): Path {
            workspace.createDirectories()
            modelPath("Verification.sysml").copyTo(workspace.resolve("Verification.sysml"), overwrite = true)
            return Path("..", "models", "etc", GUARDED_INITIAL_TRANSITION_MODEL).copyTo(workspace.resolve(GUARDED_INITIAL_TRANSITION_MODEL), overwrite = true)
        }

        fun testArtifactRoot(testClass: Class<*>): Path {
            return Path("build", "test-artifacts", testClass.simpleName)
        }

        fun modelPath(name: String): Path {
            return MODELS_ROOT.resolve(name)
        }
    }
}
