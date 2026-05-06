/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.semantics

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.library.ClasspathBasedOxstsLibrary
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.VerificationResult
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

private val sharedSysMLv2Compiler = SysMLv2Compiler()

class SysMLv2FrontendConfig(
    val sourcePath: Path,
    val variant: SysMLv2Variant,
    val libraryPaths: List<Path>,
    val cliPath: Path,
    val sysmlLibraryPath: Path,
    val oxstsInjector: Injector,
    val portfolio: VerificationPortfolio,
    val environment: ExecutionEnvironment,
    val timeout: Duration?,
    val maxConcurrency: Int?,
    val optimization: OptimizationConfig?,
    val artifacts: ArtifactConfig,
    val outputDirectory: Path,
)

class SysMLv2Frontend private constructor(
    private val semantifyrLoader: SemantifyrLoader,
    private val verificationCaseDiscoverer: VerificationCaseDiscoverer,
    private val sysmlConfig: SysMLv2FrontendConfig,
    private val compiledOxstsPath: Path,
) {

    private val logger by loggerFactory()

    val variant: SysMLv2Variant
        get() = sysmlConfig.variant

    val portfolio: VerificationPortfolio
        get() = sysmlConfig.portfolio

    val outputDirectory: Path
        get() = sysmlConfig.outputDirectory

    val timeout: Duration?
        get() = sysmlConfig.timeout

    val modelContext: SemantifyrModelContext by lazy {
        logger.info { "Loading compiled SysMLv2 model context for '$compiledOxstsPath'" }
        semantifyrLoader.startContext()
            .loadLibraryPaths(sysmlConfig.libraryPaths)
            .loadModel(compiledOxstsPath)
            .buildAndResolve()
    }

    fun verificationCases(): List<VerificationCase> {
        return verificationCaseDiscoverer.discover(modelContext)
    }

    suspend fun verify(
        case: VerificationCase,
        progress: ProgressContext = ProgressContext.NoOp,
    ): VerificationResult {
        val verifier = buildVerifier(sysmlConfig.outputDirectory.resolve("verification"))
        logger.info { "Verifying SysMLv2 case '${case.qualifiedName}' (variant=${variant.name})" }
        val result = verifier.verify(case, progress)
        logger.info { "SysMLv2 case '${case.qualifiedName}' -> ${result.verdict}" }
        return result
    }

    fun verifyBlocking(
        case: VerificationCase,
        progress: ProgressContext = ProgressContext.NoOp,
    ): VerificationResult {
        return runBlocking {
            verify(case, progress)
        }
    }

    private fun buildVerifier(outputPath: Path): SemantifyrVerifier {
        val verifierBuilder = SemantifyrVerifier.builder()
            .injector(sysmlConfig.oxstsInjector)
            .portfolio(sysmlConfig.portfolio)
            .artifacts(sysmlConfig.artifacts)
            .outputDirectory(outputPath)
            .environment(sysmlConfig.environment)

        sysmlConfig.timeout?.let {
            verifierBuilder.timeout(it)
        }
        sysmlConfig.maxConcurrency?.let {
            verifierBuilder.maxConcurrency(it)
        }
        sysmlConfig.optimization?.let {
            verifierBuilder.optimization(it)
        }

        return verifierBuilder.build()
    }

    class Builder internal constructor() {
        private val logger by loggerFactory()

        private var sourcePath: Path? = null
        private var variant = SysMLv2Variant.Default
        private var libraryOverride: List<Path>? = null
        private var cliPath: Path = Path.of("build", "cli", "index.js")
        private var sysmlLibraryPath: Path = Path.of("build", "cli", "sysml.library")
        private var oxstsInjector: Injector? = null
        private var loader: SemantifyrLoader? = null
        private var portfolio: VerificationPortfolio? = null
        private var environment = ExecutionEnvironment.Empty
        private var timeout: Duration? = null
        private var maxConcurrency: Int? = null
        private var optimization: OptimizationConfig? = null
        private var artifacts: ArtifactConfig? = null
        private var outputDirectory: Path? = null
        private var compiledOxstsOverride: Path? = null

        fun source(path: Path): Builder {
            this.sourcePath = path
            return this
        }

        fun variant(variant: SysMLv2Variant): Builder {
            this.variant = variant
            return this
        }

        fun libraryPaths(paths: List<Path>): Builder {
            this.libraryOverride = paths
            return this
        }

        fun cliPath(path: Path): Builder {
            this.cliPath = path
            return this
        }

        fun sysmlLibraryPath(path: Path): Builder {
            this.sysmlLibraryPath = path
            return this
        }

        fun injector(injector: Injector): Builder {
            this.oxstsInjector = injector
            return this
        }

        fun loader(loader: SemantifyrLoader): Builder {
            this.loader = loader
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

        fun compiledOxstsPath(path: Path): Builder {
            this.compiledOxstsOverride = path
            return this
        }

        fun build(): SysMLv2Frontend {
            val resolvedSource = requireNotNull(sourcePath) {
                "SysMLv2Frontend.Builder requires .source(...)."
            }
            val resolvedPortfolio = requireNotNull(portfolio) {
                "SysMLv2Frontend.Builder requires .portfolio(...)."
            }
            val resolvedArtifacts = requireNotNull(artifacts) {
                "SysMLv2Frontend.Builder requires .artifacts(...)."
            }
            val resolvedOutputDirectory = requireNotNull(outputDirectory) {
                "SysMLv2Frontend.Builder requires .outputDirectory(...)."
            }

            val effectiveOxstsInjector = oxstsInjector ?: OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
            val effectiveLoader = loader ?: effectiveOxstsInjector.getInstance(SemantifyrLoader::class.java)
            val discoverer = effectiveOxstsInjector.getInstance(VerificationCaseDiscoverer::class.java)

            val resolvedLibraryPaths = libraryOverride ?: extractVariantLibrary()

            val compiledOxsts = compiledOxstsOverride ?: run {
                val target = resolvedOutputDirectory
                    .resolve("compiled")
                    .resolve(variant.name.lowercase())
                    .resolve("${resolvedSource.nameWithoutExtension}.oxsts")
                sharedSysMLv2Compiler.compile(
                    sourcePath = resolvedSource,
                    outputPath = target,
                    cliPath = cliPath,
                    sysmlLibraryPath = sysmlLibraryPath,
                )
            }

            logger.info {
                "Building SysMLv2Frontend (variant=${variant.name}, source=$resolvedSource, compiled=$compiledOxsts, libraryPaths=$resolvedLibraryPaths)"
            }

            val sysmlConfig = SysMLv2FrontendConfig(
                sourcePath = resolvedSource,
                variant = variant,
                libraryPaths = resolvedLibraryPaths,
                cliPath = cliPath,
                sysmlLibraryPath = sysmlLibraryPath,
                oxstsInjector = effectiveOxstsInjector,
                portfolio = resolvedPortfolio,
                environment = environment,
                timeout = timeout,
                maxConcurrency = maxConcurrency,
                optimization = optimization,
                artifacts = resolvedArtifacts,
                outputDirectory = resolvedOutputDirectory,
            )

            return SysMLv2Frontend(
                semantifyrLoader = effectiveLoader,
                verificationCaseDiscoverer = discoverer,
                sysmlConfig = sysmlConfig,
                compiledOxstsPath = compiledOxsts,
            )
        }

        private fun extractVariantLibrary(): List<Path> {
            val libraryPath = Path.of(
                System.getProperty("user.home"),
                ".semantifyr",
                "sysmlv2",
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
