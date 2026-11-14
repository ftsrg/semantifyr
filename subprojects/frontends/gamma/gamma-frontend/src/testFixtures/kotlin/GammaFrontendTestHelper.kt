/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.testing

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.frontends.gamma.GammaFrontend
import hu.bme.mit.semantifyr.frontends.gamma.GammaVariant
import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationCase
import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationResult
import hu.bme.mit.semantifyr.frontends.gamma.discovery.GammaVerificationCaseDiscoverer
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EF
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaRoot
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.assertVerdict
import hu.bme.mit.semantifyr.verifier.assumePortfolioAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.provider.Arguments
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.streams.asStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class GammaTestEngine(
    val portfolio: VerificationPortfolio,
) {
    Theta(Portfolios.ThetaBoundedKInduction),
    Nuxmv(Portfolios.NuxmvIc3Invar),
    Spin(Portfolios.SpinSafeDfs),
    Uppaal(Portfolios.UppaalDefault),
}

class GammaFrontendTestHelper {

    private val gammaInjector = GammaStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val oxstsInjector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val gammaReader = gammaInjector.getInstance(GammaReader::class.java)
    private val workspaces = mutableMapOf<Path, Map<Path, GammaRoot>>()
    private val gammaDiscoverer = gammaInjector.getInstance(GammaVerificationCaseDiscoverer::class.java)

    fun discover(sourcePath: Path): List<GammaVerificationCase> {
        val gammaModel = gammaReader.readGammaFile(sourcePath.toFile())
        return gammaDiscoverer.discover(gammaModel)
    }

    fun discoverInWorkspace(
        workspace: Path,
        file: String,
    ): List<GammaVerificationCase> {
        val roots = workspaces.getOrPut(workspace.toAbsolutePath().normalize()) {
            val files = gammaReader.workspaceFiles(workspace)
            files.zip(gammaReader.readGammaFiles(files)).associate { (workspaceFile, root) ->
                workspaceFile.toPath().toAbsolutePath().normalize() to root
            }
        }
        val gammaModel = roots.getValue(workspace.resolve(file).toAbsolutePath().normalize()) as GammaModelPackage
        return gammaDiscoverer.discover(gammaModel)
    }

    fun witnessCasesOnEveryEngine(): Stream<Arguments> {
        return GammaCorpus.load(GammaCorpus.examples).asSequence().flatMap { entry ->
            val gammaVerificationCase = discoverInWorkspace(entry.directory, entry.model.file).single {
                it.declaration.name == entry.case.name
            }
            if (gammaVerificationCase.declaration.expression is EF) {
                GammaTestEngine.entries.asSequence().map {
                    Arguments.of(Named.of(gammaVerificationCase.qualifiedName, entry), it)
                }
            } else {
                emptySequence()
            }
        }.asStream()
    }

    fun discoverAsArguments(sourcePath: Path): Stream<Arguments> {
        return discover(sourcePath).asSequence().map {
            Arguments.of(Named.of(it.qualifiedName, it))
        }.asStream()
    }

    fun buildFrontend(
        portfolio: VerificationPortfolio,
        outputDirectory: Path,
        variant: GammaVariant = GammaVariant.Default,
        timeout: Duration = 30.minutes,
        artifacts: ArtifactConfig = ArtifactConfig.ALL,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
    ): GammaFrontend {
        return GammaFrontend.builder()
            .variant(variant)
            .gammaInjector(gammaInjector)
            .oxstsInjector(oxstsInjector)
            .portfolio(portfolio)
            .environment(environment)
            .timeout(timeout)
            .artifacts(artifacts)
            .outputDirectory(outputDirectory)
            .build()
    }

    suspend fun checkVerificationCase(
        frontend: GammaFrontend,
        gammaVerificationCase: GammaVerificationCase,
        expectedVerdict: VerificationVerdict,
    ): GammaVerificationResult {
        val result = frontend.verify(gammaVerificationCase)
        assertVerdict(gammaVerificationCase.qualifiedName, expectedVerdict, result.verification)
        return result
    }

    suspend fun checkVerificationCaseOn(
        engine: GammaTestEngine,
        entry: GammaCorpusEntry,
        outputDirectory: Path,
        timeout: Duration,
        validationPortfolio: VerificationPortfolio? = null,
    ): GammaVerificationResult {
        val portfolio = engine.portfolio
        assumePortfolioAvailable(portfolio)

        val gammaVerificationCase = discoverInWorkspace(entry.directory, entry.model.file).single {
            it.declaration.name == entry.case.name
        }
        val frontend = buildFrontend(portfolio, outputDirectory.resolve(engine.name.lowercase()), timeout = timeout)
        val result = frontend.verify(gammaVerificationCase)
        assertVerdict(gammaVerificationCase.qualifiedName, entry.case.expectedVerdict, result.verification)

        val witness = result.witness
        if (validationPortfolio != null && witness != null) {
            val outcome = frontend.validateWitness(gammaVerificationCase, witness.witnessPath, validationPortfolio)
            assertThat(outcome.isValid)
                .describedAs(
                    "Witness of ${portfolio.id} for ${gammaVerificationCase.qualifiedName} must re-verify Passed via ${validationPortfolio.id} but got ${outcome.verification.verdict} (${outcome.verification.message ?: "no message"})",
                )
                .isTrue()
        }
        return result
    }

    companion object {
        @JvmStatic
        fun testArtifactRoot(testClass: Class<*>): Path {
            return Path("build", "test-artifacts", testClass.simpleName)
        }
    }
}
