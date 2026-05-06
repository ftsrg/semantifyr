/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.library.ClasspathBasedOxstsLibrary
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.Trace
import hu.bme.mit.semantifyr.verifier.VerificationResult
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlinx.coroutines.runBlocking
import org.eclipse.xtext.EcoreUtil2
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

data class GammaVerificationCase(
    val qualifiedName: String,
    val declaration: VerificationCaseDeclaration,
)

class GammaWitness(
    val trace: Trace,
    val witnessPath: Path,
    val compiledModelPath: Path,
)

data class GammaVerificationResult(
    val verification: VerificationResult,
    val witness: GammaWitness?,
)

enum class GammaVariant(
    internal val resourcePrefix: String,
) {
    Default("default"),
}

class GammaFrontendConfig(
    val libraryPaths: List<Path>,
    val oxstsInjector: Injector,
    val portfolio: VerificationPortfolio,
    val environment: ExecutionEnvironment,
    val timeout: Duration?,
    val maxConcurrency: Int?,
    val optimization: OptimizationConfig?,
    val artifacts: ArtifactConfig,
    val outputDirectory: Path,
)

class GammaFrontend @Inject private constructor(
    private val gammaCompiler: GammaCompiler,
    private val semantifyrLoader: SemantifyrLoader,
    private val verificationCaseDiscoverer: VerificationCaseDiscoverer,
    private val gammaFrontendConfig: GammaFrontendConfig,
) {

    private val logger by loggerFactory()

    suspend fun verify(
        gammaVerificationCase: GammaVerificationCase,
        progress: ProgressContext = ProgressContext.NoOp,
    ): GammaVerificationResult {
        val qualifiedName = gammaVerificationCase.qualifiedName
        val baseDirectory = gammaFrontendConfig.outputDirectory.resolve(qualifiedName)
        val outputPath = baseDirectory.resolve("model.oxsts")
        val gammaModel = EcoreUtil2.getContainerOfType(gammaVerificationCase.declaration, GammaModelPackage::class.java)

        logger.info { "Verifying Gamma case '$qualifiedName'" }

        gammaCompiler.compile(gammaModel, outputPath)

        val context = semantifyrLoader.startContext()
            .loadLibraryPaths(gammaFrontendConfig.libraryPaths)
            .loadModel(outputPath)
            .buildAndResolve()

        val oxstsVerificationCaseName = qualifiedName.replace(".", "::")
        val oxstsVerificationCase = verificationCaseDiscoverer.findByQualifiedName(context, oxstsVerificationCaseName)

        val semantifyrVerifier = buildVerifier(baseDirectory.resolve("verification"))

        val result = semantifyrVerifier.verify(oxstsVerificationCase, progress)
        val witness = result.trace?.let {
            val witnessUri = it.backAnnotatedModel.eResource().uri
            require(witnessUri.isFile) {
                "Witness resource has no file URI, got '$witnessUri'"
            }
            GammaWitness(it, Path.of(witnessUri.toFileString()), outputPath)
        }
        logger.info { "Gamma case '$qualifiedName' -> ${result.verdict}" }
        if (witness != null) {
            logger.info { "Gamma case '$qualifiedName' produced witness at '${witness.witnessPath}'" }
        }
        return GammaVerificationResult(result, witness)
    }

    fun verifyBlocking(
        case: GammaVerificationCase,
        progress: ProgressContext = ProgressContext.NoOp,
    ): GammaVerificationResult {
        return runBlocking {
            verify(case, progress)
        }
    }

    // TODO: Re-enable once back-annotated witness output type-checks against the OXSTS validator.
    //       The current witness emits e.g. `port.outputEvent == port.interface.start` which the
    //       validator rejects with TYPE_MISMATCH. Reloading the witness via SemantifyrLoader
    //       therefore fails. This needs a fix in the back-annotator (or the gamma library).
    // suspend fun validateWitness(
    //     gammaVerificationCase: GammaVerificationCase,
    //     witness: GammaWitness,
    //     progress: ProgressContext = ProgressContext.NoOp,
    // ): WitnessValidationResult {
    //     val qualifiedName = gammaVerificationCase.qualifiedName
    //     val baseDirectory = gammaFrontendConfig.outputDirectory.resolve(qualifiedName)
    //     val verifier = buildVerifier(baseDirectory.resolve("witness-validation"))
    //
    //     logger.info { "Validating witness for Gamma case '$qualifiedName' from '${witness.witnessPath}'" }
    //
    //     val backAnnotatedModel = loadWitnessModel(witness)
    //     val result = witnessValidator.validate(verifier, backAnnotatedModel, progress)
    //     logger.info {
    //         "Witness validation for Gamma case '$qualifiedName' -> ${result.verification.verdict} (${result::class.simpleName})"
    //     }
    //     return result
    // }
    //
    // private fun loadWitnessModel(witness: GammaWitness): InlinedOxsts {
    //     require(Files.isRegularFile(witness.witnessPath)) {
    //         "Witness file does not exist: '${witness.witnessPath}'"
    //     }
    //     require(Files.isRegularFile(witness.compiledModelPath)) {
    //         "Compiled OXSTS model does not exist: '${witness.compiledModelPath}'"
    //     }
    //
    //     val context = semantifyrLoader.startContext()
    //         .loadLibraryPaths(gammaFrontendConfig.libraryPaths)
    //         .loadModel(witness.compiledModelPath)
    //         .loadModel(witness.witnessPath)
    //         .buildAndResolve()
    //
    //     return context.modelResources
    //         .asSequence()
    //         .flatMap { it.contents.asSequence() }
    //         .filterIsInstance<InlinedOxsts>()
    //         .firstOrNull()
    //         ?: error("Witness file '${witness.witnessPath}' did not contain an InlinedOxsts")
    // }
    //
    // fun validateWitnessBlocking(
    //     gammaVerificationCase: GammaVerificationCase,
    //     witness: GammaWitness,
    //     progress: ProgressContext = ProgressContext.NoOp,
    // ): WitnessValidationResult {
    //     return runBlocking {
    //         validateWitness(gammaVerificationCase, witness, progress)
    //     }
    // }

    private fun buildVerifier(outputPath: Path): SemantifyrVerifier {
        val verifierBuilder = SemantifyrVerifier.builder()
            .injector(gammaFrontendConfig.oxstsInjector)
            .portfolio(gammaFrontendConfig.portfolio)
            .artifacts(gammaFrontendConfig.artifacts)
            .outputDirectory(outputPath)
            .environment(gammaFrontendConfig.environment)

        gammaFrontendConfig.timeout?.let {
            verifierBuilder.timeout(it)
        }
        gammaFrontendConfig.maxConcurrency?.let {
            verifierBuilder.maxConcurrency(it)
        }
        gammaFrontendConfig.optimization?.let {
            verifierBuilder.optimization(it)
        }

        return verifierBuilder.build()
    }

    class Builder internal constructor() {
        private val logger by loggerFactory()

        private var variant = GammaVariant.Default
        private var libraryOverride: List<Path>? = null
        private var gammaInjector: Injector? = null
        private var oxstsInjector: Injector? = null
        private var portfolio: VerificationPortfolio? = null
        private var environment = ExecutionEnvironment.Empty
        private var timeout: Duration? = null
        private var maxConcurrency: Int? = null
        private var optimization: OptimizationConfig? = null
        private var artifacts: ArtifactConfig? = null
        private var outputDirectory: Path? = null

        fun variant(variant: GammaVariant): Builder {
            this.variant = variant
            return this
        }

        fun libraryPaths(paths: List<Path>): Builder {
            this.libraryOverride = paths
            return this
        }

        fun gammaInjector(injector: Injector): Builder {
            this.gammaInjector = injector
            return this
        }

        fun oxstsInjector(injector: Injector): Builder {
            this.oxstsInjector = injector
            return this
        }

        fun portfolio(portfolio: VerificationPortfolio): Builder {
            this.portfolio = portfolio
            return this
        }

        fun environment(environment: ExecutionEnvironment): Builder {
            this.environment = environment
            return this
        }

        fun timeout(timeout: Duration): Builder {
            this.timeout = timeout
            return this
        }

        fun timeout(timeout: java.time.Duration): Builder {
            return timeout(timeout.toKotlinDuration())
        }

        fun maxConcurrency(limit: Int): Builder {
            this.maxConcurrency = limit
            return this
        }

        fun optimization(config: OptimizationConfig): Builder {
            this.optimization = config
            return this
        }

        fun artifacts(config: ArtifactConfig): Builder {
            this.artifacts = config
            return this
        }

        fun outputDirectory(path: Path): Builder {
            this.outputDirectory = path
            return this
        }

        fun build(): GammaFrontend {
            val resolvedPortfolio = requireNotNull(portfolio) {
                "GammaFrontend.Builder requires .portfolio(...)."
            }
            val resolvedArtifacts = requireNotNull(artifacts) {
                "GammaFrontend.Builder requires .artifacts(...)."
            }
            val resolvedOutputDirectory = requireNotNull(outputDirectory) {
                "GammaFrontend.Builder requires .outputDirectory(...)."
            }

            val effectiveGammaInjector = gammaInjector ?: GammaStandaloneSetup().createInjectorAndDoEMFRegistration()
            val effectiveOxstsInjector = oxstsInjector ?: OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()

            val compiler = effectiveGammaInjector.getInstance(GammaCompiler::class.java)
            val loader = effectiveOxstsInjector.getInstance(SemantifyrLoader::class.java)
            val discoverer = effectiveOxstsInjector.getInstance(VerificationCaseDiscoverer::class.java)

            val resolvedLibraryPaths = libraryOverride ?: extractVariantLibrary()
            logger.info { "Building GammaFrontend (variant=${variant.name}, libraryPaths=$resolvedLibraryPaths)" }

            val gammaFrontendConfig = GammaFrontendConfig(
                libraryPaths = resolvedLibraryPaths,
                oxstsInjector = effectiveOxstsInjector,
                portfolio = resolvedPortfolio,
                environment = environment,
                timeout = timeout,
                maxConcurrency = maxConcurrency,
                optimization = optimization,
                artifacts = resolvedArtifacts,
                outputDirectory = resolvedOutputDirectory,
            )

            return GammaFrontend(
                gammaCompiler = compiler,
                semantifyrLoader = loader,
                verificationCaseDiscoverer = discoverer,
                gammaFrontendConfig = gammaFrontendConfig,
            )
        }

        private fun extractVariantLibrary(): List<Path> {
            val libraryPath = Path.of(
                System.getProperty("user.home"),
                ".semantifyr",
                "gamma",
                variant.name.lowercase(),
            )
            val library = ClasspathBasedOxstsLibrary(
                javaClass.classLoader,
                variant.resourcePrefix,
                libraryPath,
            )
            library.prepareLoading()
            return listOf(library.extractedLibraryPath)
        }
    }

    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }
}
