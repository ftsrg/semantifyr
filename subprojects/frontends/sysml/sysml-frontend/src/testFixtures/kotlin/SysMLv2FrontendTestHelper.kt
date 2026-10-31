/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.testing

import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Frontend
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Variant
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.junit.jupiter.api.Assertions
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SysMLv2FrontendTestHelper {

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

        fun testArtifactRoot(testClass: Class<*>): Path {
            return Path("build", "test-artifacts", testClass.simpleName)
        }

        fun modelPath(name: String): Path {
            return MODELS_ROOT.resolve(name)
        }
    }
}
