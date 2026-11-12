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
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ExecutionTrace
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaNameMap
import hu.bme.mit.semantifyr.frontends.gamma.serialization.markName
import hu.bme.mit.semantifyr.frontends.gamma.serialization.markQualifiedName
import hu.bme.mit.semantifyr.frontends.gamma.trace.GammaTraceGenerator
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.library.ClasspathBasedOxstsLibrary
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.Trace
import hu.bme.mit.semantifyr.verifier.TraceDto
import hu.bme.mit.semantifyr.verifier.VerificationResult
import hu.bme.mit.semantifyr.verifier.VerificationResultDto
import hu.bme.mit.semantifyr.verifier.WitnessValidationResultDto
import hu.bme.mit.semantifyr.verifier.caseReportPath
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.toJavaDto
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidator
import kotlinx.coroutines.runBlocking
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
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
    val gammaTrace: ExecutionTrace,
    val gammaTracePath: Path,
)

data class GammaVerificationResult(
    val verification: VerificationResult,
    val witness: GammaWitness?,
    val reportPath: Path,
)

data class GammaWitnessDto(
    val trace: TraceDto,
    val witnessPath: Path,
    val compiledModelPath: Path,
    val gammaTracePath: Path,
) {
    companion object {
        fun fromGammaWitness(witness: GammaWitness): GammaWitnessDto {
            return GammaWitnessDto(
                trace = witness.trace.toJavaDto(),
                witnessPath = witness.witnessPath,
                compiledModelPath = witness.compiledModelPath,
                gammaTracePath = witness.gammaTracePath,
            )
        }
    }
}

data class GammaVerificationResultDto(
    val verification: VerificationResultDto,
    val witness: GammaWitnessDto?,
) {
    companion object {
        fun fromGammaResult(result: GammaVerificationResult): GammaVerificationResultDto {
            return GammaVerificationResultDto(
                verification = result.verification.toJavaDto(),
                witness = result.witness?.let { GammaWitnessDto.fromGammaWitness(it) },
            )
        }
    }
}

fun GammaVerificationResult.toJavaDto(): GammaVerificationResultDto {
    return GammaVerificationResultDto.fromGammaResult(this)
}

fun GammaWitness.toJavaDto(): GammaWitnessDto {
    return GammaWitnessDto.fromGammaWitness(this)
}

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
    private val gammaTraceGenerator: GammaTraceGenerator,
    private val gammaSerializer: ISerializer,
    private val semantifyrLoader: SemantifyrLoader,
    private val verificationCaseDiscoverer: VerificationCaseDiscoverer,
    private val gammaFrontendConfiguration: GammaFrontendConfig,
) {

    private val logger by loggerFactory()

    val outputDirectory: Path
        get() = gammaFrontendConfiguration.outputDirectory

    val timeout: Duration?
        get() = gammaFrontendConfiguration.timeout

    suspend fun verify(
        gammaVerificationCase: GammaVerificationCase,
        progress: ProgressContext = ProgressContext.Silent,
    ): GammaVerificationResult {
        val qualifiedName = gammaVerificationCase.qualifiedName
        val baseDirectory = caseDirectory(gammaVerificationCase)
        val gammaModel = EcoreUtil2.getContainerOfType(gammaVerificationCase.declaration, GammaModelPackage::class.java)

        logger.info { "Verifying Gamma case '$qualifiedName'" }

        val compiledModel = gammaCompiler.compileWithImports(gammaModel, baseDirectory)

        val context = semantifyrLoader.startContext()
            .loadLibraryPaths(gammaFrontendConfiguration.libraryPaths)
            .loadModelPaths(compiledModel.importedPaths + listOf(compiledModel.modelPath))
            .buildAndResolve()

        val oxstsVerificationCaseName = "${markQualifiedName(gammaModel.name)}::${markName(gammaVerificationCase.declaration.name)}"
        val oxstsVerificationCase = verificationCaseDiscoverer.findByQualifiedName(context, oxstsVerificationCaseName)

        val verificationDirectory = baseDirectory.resolve("verification")
        val semantifyrVerifier = buildVerifier(verificationDirectory)

        val result = semantifyrVerifier.verify(oxstsVerificationCase, progress)
        val witness = result.trace?.let {
            val witnessUri = it.backAnnotatedModel.eResource().uri
            require(witnessUri.isFile) {
                "Witness resource has no file URI, got '$witnessUri'"
            }
            val witnessPath = Path.of(witnessUri.toFileString())
            val gammaTracePath = witnessPath.resolveSibling("trace.get")
            val gammaTrace = writeGammaTrace(gammaVerificationCase, it, compiledModel.nameMap, gammaTracePath)
            GammaWitness(it, witnessPath, compiledModel.modelPath, gammaTrace, gammaTracePath)
        }
        logger.info { "Gamma case '$qualifiedName' -> ${result.verdict}" }
        if (witness != null) {
            logger.info { "Gamma case '$qualifiedName' produced witness at '${witness.witnessPath}'" }
        }
        return GammaVerificationResult(result, witness, caseReportPath(verificationDirectory, oxstsVerificationCase))
    }

    private fun writeGammaTrace(
        gammaVerificationCase: GammaVerificationCase,
        trace: Trace,
        nameMap: GammaNameMap,
        gammaTracePath: Path,
    ): ExecutionTrace {
        val gammaTrace = gammaTraceGenerator.generate(gammaVerificationCase.declaration, trace.callTrace, trace.witnessState, nameMap)
        gammaTracePath.writeText(gammaSerializer.serialize(gammaTrace, SaveOptions.newBuilder().format().options))
        logger.info { "Gamma case '${gammaVerificationCase.qualifiedName}' wrote its Gamma trace to '$gammaTracePath'" }
        return gammaTrace
    }

    fun verifyBlocking(
        case: GammaVerificationCase,
        progress: ProgressContext = ProgressContext.Silent,
    ): GammaVerificationResultDto {
        return runBlocking {
            verify(case, progress).toJavaDto()
        }
    }

    // a verify run puts its one case's artifacts, the witness among them, in a single directory under verification/
    fun findWitness(gammaVerificationCase: GammaVerificationCase): Path? {
        val verificationDirectory = caseDirectory(gammaVerificationCase).resolve("verification")
        if (!verificationDirectory.isDirectory()) {
            return null
        }
        return verificationDirectory.listDirectoryEntries().map {
            it.resolve("witness.oxsts")
        }.singleOrNull {
            it.isRegularFile()
        }
    }

    suspend fun validateWitness(
        gammaVerificationCase: GammaVerificationCase,
        witnessPath: Path,
        portfolio: VerificationPortfolio = gammaFrontendConfiguration.portfolio,
        progress: ProgressContext = ProgressContext.Silent,
    ): WitnessValidationResult {
        val qualifiedName = gammaVerificationCase.qualifiedName
        val baseDirectory = caseDirectory(gammaVerificationCase)
        val verifier = buildVerifier(baseDirectory.resolve("verification"), portfolio)

        logger.info { "Validating witness for Gamma case '$qualifiedName' from '$witnessPath'" }

        val backAnnotatedModel = loadWitnessModel(witnessPath, baseDirectory)
        val result = WitnessValidator().validate(verifier, backAnnotatedModel, progress)
        logger.info {
            "Witness validation for Gamma case '$qualifiedName' -> ${result.verification.verdict} (${result::class.simpleName})"
        }
        return result
    }

    private fun loadWitnessModel(witnessPath: Path, baseDirectory: Path): InlinedOxsts {
        val compiledModelPath = baseDirectory.resolve(GammaCompiler.MODEL_FILE_NAME)
        require(witnessPath.isRegularFile()) {
            "Witness file does not exist: '$witnessPath'"
        }
        require(compiledModelPath.isRegularFile()) {
            "Compiled OXSTS model does not exist: '$compiledModelPath'"
        }
        val importsDirectory = GammaCompiler.importsDirectory(baseDirectory)
        val importedPaths = if (importsDirectory.isDirectory()) {
            importsDirectory.listDirectoryEntries("*.oxsts")
        } else {
            emptyList()
        }

        val context = semantifyrLoader.startContext()
            .loadLibraryPaths(gammaFrontendConfiguration.libraryPaths)
            .loadModelPaths(importedPaths + listOf(compiledModelPath, witnessPath))
            .buildAndResolve()

        return context.modelResources
            .asSequence()
            .flatMap { it.contents.asSequence() }
            .filterIsInstance<InlinedOxsts>()
            .firstOrNull()
            ?: error("Witness file '$witnessPath' did not contain an InlinedOxsts")
    }

    fun validateWitnessBlocking(
        gammaVerificationCase: GammaVerificationCase,
        witnessPath: Path,
        progress: ProgressContext = ProgressContext.Silent,
    ): WitnessValidationResultDto {
        return runBlocking {
            validateWitness(gammaVerificationCase, witnessPath, progress = progress).toJavaDto()
        }
    }

    private fun caseDirectory(gammaVerificationCase: GammaVerificationCase): Path {
        return gammaFrontendConfiguration.outputDirectory.resolve(gammaVerificationCase.qualifiedName)
    }

    private fun buildVerifier(
        outputPath: Path,
        portfolioOverride: VerificationPortfolio = gammaFrontendConfiguration.portfolio,
    ): SemantifyrVerifier {
        val verifierBuilder = SemantifyrVerifier.builder()
            .injector(gammaFrontendConfiguration.oxstsInjector)
            .portfolio(portfolioOverride)
            .artifacts(gammaFrontendConfiguration.artifacts)
            .outputDirectory(outputPath)
            .environment(gammaFrontendConfiguration.environment)

        gammaFrontendConfiguration.timeout?.let {
            verifierBuilder.timeout(it)
        }
        gammaFrontendConfiguration.maxConcurrency?.let {
            verifierBuilder.maxConcurrency(it)
        }
        gammaFrontendConfiguration.optimization?.let {
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

        fun optimization(configuration: OptimizationConfig): Builder {
            this.optimization = configuration
            return this
        }

        fun artifacts(configuration: ArtifactConfig): Builder {
            this.artifacts = configuration
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
            val effectiveGammaInjector = requireNotNull(gammaInjector) {
                "GammaFrontend.Builder requires .gammaInjector(...)."
            }
            val effectiveOxstsInjector = requireNotNull(oxstsInjector) {
                "GammaFrontend.Builder requires .oxstsInjector(...)."
            }

            val compiler = effectiveGammaInjector.getInstance(GammaCompiler::class.java)
            val traceGenerator = effectiveGammaInjector.getInstance(GammaTraceGenerator::class.java)
            val gammaSerializer = effectiveGammaInjector.getInstance(ISerializer::class.java)
            val loader = effectiveOxstsInjector.getInstance(SemantifyrLoader::class.java)
            val discoverer = effectiveOxstsInjector.getInstance(VerificationCaseDiscoverer::class.java)

            val resolvedLibraryPaths = libraryOverride ?: extractVariantLibrary()
            logger.info { "Building GammaFrontend (variant=${variant.name}, libraryPaths=$resolvedLibraryPaths)" }

            val gammaFrontendConfiguration = GammaFrontendConfig(
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
                gammaTraceGenerator = traceGenerator,
                gammaSerializer = gammaSerializer,
                semantifyrLoader = loader,
                verificationCaseDiscoverer = discoverer,
                gammaFrontendConfiguration = gammaFrontendConfiguration,
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
