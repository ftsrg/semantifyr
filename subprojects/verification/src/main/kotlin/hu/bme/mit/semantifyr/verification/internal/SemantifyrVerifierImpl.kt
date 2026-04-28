/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.internal

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backend.VerificationMetrics
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.qualifiedNameToDirectoryName
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitness
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitnessState
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactKind
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.BooleanEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.verification.ArtifactPointersReport
import hu.bme.mit.semantifyr.verification.ConfigurationReport
import hu.bme.mit.semantifyr.verification.PortfolioReport
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.SemantifyrVerifier
import hu.bme.mit.semantifyr.verification.VerificationCaseReport
import hu.bme.mit.semantifyr.verification.VerificationOutcomeReport
import hu.bme.mit.semantifyr.verification.VerificationReport
import hu.bme.mit.semantifyr.verification.VerificationResult
import hu.bme.mit.semantifyr.verification.VerificationTrace
import hu.bme.mit.semantifyr.verification.discovery.CaseFilter
import hu.bme.mit.semantifyr.verification.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verification.portfolio.LimitedBackendExecutor
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verification.witness.AssumptionWitnessBackAnnotator
import hu.bme.mit.semantifyr.verification.witness.CallTraceTransformer
import hu.bme.mit.semantifyr.verification.witness.OxstsClassAssumptionWitnessTransformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

private val CANCEL_POLL_INTERVAL = 250.milliseconds

class SemantifyrVerifierImpl(
    private val injector: Injector,
    private val context: SemantifyrModelContext,
    private val verificationPortfolio: VerificationPortfolio,
    private val artifactConfig: ArtifactConfig,
    private val outputDirectory: Path,
    private val environment: ExecutionEnvironment,
    private val timeout: Duration,
    maxConcurrency: Int,
    private val optimizationConfig: OptimizationConfig,
) : SemantifyrVerifier {

    private val logger by loggerFactory()

    private val discoverer = injector.getInstance(VerificationCaseDiscoverer::class.java)

    private val constantEvaluatorProvider = injector.getInstance(ConstantExpressionEvaluatorProvider::class.java)

    private val classWitnessTransformer = injector.getInstance(OxstsClassAssumptionWitnessTransformer::class.java)
    private val witnessBackAnnotator = injector.getInstance(AssumptionWitnessBackAnnotator::class.java)
    private val callTraceTransformer = injector.getInstance(CallTraceTransformer::class.java)
    private val oxstsSerializer = injector.getInstance(ISerializer::class.java)

    private val executor = LimitedBackendExecutor(maxConcurrency)

    private val compiler = SemantifyrCompiler(injector, artifactConfig, optimizationConfig)

    private fun caseDirectoryFor(qualifiedName: String): Path {
        return outputDirectory.resolve(qualifiedNameToDirectoryName(qualifiedName))
    }

    private fun prepareCaseDirectory(qualifiedName: String): Path {
        val caseDirectory = caseDirectoryFor(qualifiedName)
        if (caseDirectory.toFile().exists()) {
            caseDirectory.toFile().deleteRecursively()
        }
        caseDirectory.createDirectories()
        return caseDirectory
    }

    private val cases by lazy {
        val discovered = discoverer.discover(context)
        logger.info { "Discovered ${discovered.size} verification case(s) in the loaded model" }
        discovered
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    override fun modelContext(): SemantifyrModelContext {
        return context
    }

    override fun verificationCases(filter: CaseFilter): List<VerificationCase> {
        return cases.filter(filter::matches)
    }

    override suspend fun verifyAll(filter: CaseFilter, progressContext: ProgressContext): List<VerificationResult> {
        val results = mutableListOf<VerificationResult>()
        for (case in verificationCases(filter)) {
            progressContext.checkIsCancelled()
            results += verify(case, progressContext.child(case.qualifiedName))
        }
        return results
    }

    override suspend fun verify(qualifiedName: String, progressContext: ProgressContext): VerificationResult {
        logger.info { "Looking for verification case named $qualifiedName" }

        val case = cases.firstOrNull {
            it.qualifiedName == qualifiedName
        }

        if (case == null) {
            return caseNotFound(qualifiedName)
        }

        return verify(case, progressContext)
    }

    private fun caseNotFound(qualifiedName: String): VerificationResult {
        val known = cases.map {
            it.qualifiedName
        }
        val knownSummary = if (known.size <= 10) {
            known.toString()
        } else {
            "${known.take(10)} (+ ${known.size - 10} more)"
        }

        logger.warn { "No verification case with qualified name '$qualifiedName' (known: $knownSummary)" }
        return VerificationResult(
            verdict = VerificationVerdict.Errored,
            metadata = VerificationRunMetadata(
                backendId = "trivial",
                startedAt = Clock.System.now(),
                caseQualifiedName = qualifiedName,
            ),
            message = "No verification case named '$qualifiedName'.",
        )
    }

    override suspend fun verify(verificationCase: VerificationCase, progressContext: ProgressContext): VerificationResult {
        progressContext.reportProgress("Compiling ${verificationCase.qualifiedName}")
        logger.info { "Compiling '${verificationCase.qualifiedName}'" }
        val caseDirectory = prepareCaseDirectory(verificationCase.qualifiedName)
        val compilation = compiler.compile(verificationCase.classDeclaration, caseDirectory)
        return verify(compilation, verificationCase, progressContext)
    }

    override suspend fun verify(inlinedOxsts: InlinedOxsts, progressContext: ProgressContext): VerificationResult {
        val qualifiedName = inlinedOxsts.classDeclaration.name ?: "<inlined>"
        val case = VerificationCase(
            classDeclaration = inlinedOxsts.classDeclaration,
            qualifiedName = qualifiedName,
        )
        progressContext.reportProgress("Compiling $qualifiedName")
        logger.info { "Compiling inlined of '$qualifiedName'" }
        val caseDirectory = prepareCaseDirectory(qualifiedName)
        val compilation = compiler.compile(inlinedOxsts, caseDirectory)
        return verify(compilation, case, progressContext)
    }

    private suspend fun verify(
        compilation: FlattenedCompilationContext,
        case: VerificationCase,
        progress: ProgressContext,
    ): VerificationResult {
        val qualifiedName = case.qualifiedName
        logger.info { "Verifying case '$qualifiedName' with portfolio '${verificationPortfolio.id}' (timeout=$timeout)" }

        return try {
            runInTimeout(progress) {
                runVerification(compilation, case, progress)
            }
        } catch (e: TimeoutCancellationException) {
            logger.info { "Verification of '$qualifiedName' timed out after $timeout; returning Inconclusive" }
            VerificationResult(
                verdict = VerificationVerdict.Inconclusive,
                metadata = VerificationRunMetadata(
                    backendId = verificationPortfolio.id,
                    startedAt = Clock.System.now(),
                    caseQualifiedName = qualifiedName,
                ),
                message = "Timeout ($timeout) exceeded.",
            )
        } catch (c: CancellationException) {
            logger.info { "Verification of '$qualifiedName' cancelled" }
            throw c
        } catch (e: BackendUnsupportedException) {
            logger.info { "Verification of '$qualifiedName' not supported by '${verificationPortfolio.id}': ${e.message}" }
            VerificationResult(
                verdict = VerificationVerdict.NotSupported,
                metadata = VerificationRunMetadata(
                    backendId = verificationPortfolio.id,
                    startedAt = Clock.System.now(),
                    caseQualifiedName = qualifiedName,
                ),
                message = e.message ?: e::class.simpleName,
            )
        } catch (e: Exception) {
            logger.warn("Verification of '$qualifiedName' errored: ${e.message ?: e::class.simpleName}", e)
            VerificationResult(
                verdict = VerificationVerdict.Errored,
                metadata = VerificationRunMetadata(
                    backendId = verificationPortfolio.id,
                    startedAt = Clock.System.now(),
                    caseQualifiedName = qualifiedName,
                ),
                message = e.message ?: e::class.simpleName,
            )
        }
    }

    private suspend fun runVerification(
        compilationContext: FlattenedCompilationContext,
        verificationCase: VerificationCase,
        progressContext: ProgressContext,
    ): VerificationResult {
        val qualifiedName = verificationCase.qualifiedName
        val inlinedOxsts = compilationContext.inlinedOxsts
        val caseArtifactPath = caseDirectoryFor(qualifiedName)

        if (isTriviallyVerifiable(inlinedOxsts)) {
            val trivial = trivialVerdict(qualifiedName, inlinedOxsts, compilationContext, caseArtifactPath)
            writeReport(verificationCase, trivial, caseArtifactPath)
            return trivial
        }

        val request = VerificationRequest(
            case = verificationCase,
            input = inlinedOxsts,
            compilation = compilationContext,
            artifactOutputPath = caseArtifactPath,
        )

        val backendResult = verificationPortfolio.verify(request, executor, environment, progressContext)

        val result = backannotateResult(backendResult, compilationContext, qualifiedName, caseArtifactPath)

        logger.info { "Case '$qualifiedName' -> ${result.verdict} in ${result.metrics.totalDuration} - ${result.message ?: "no message"}" }

        writeReport(verificationCase, result, caseArtifactPath)

        return result
    }

    private fun writeReport(
        case: VerificationCase,
        result: VerificationResult,
        caseArtifactPath: Path,
    ) {
        if (!artifactConfig.isEnabled(ArtifactKind.Report)) {
            return
        }
        val reportPath = caseArtifactPath.resolve("report.json")
        try {
            caseArtifactPath.createDirectories()
            val report = VerificationReport(
                case = VerificationCaseReport(
                    qualifiedName = case.qualifiedName,
                    className = case.classDeclaration.name,
                ),
                portfolio = PortfolioReport(
                    id = verificationPortfolio.id,
                    displayName = verificationPortfolio.displayName,
                    familyId = verificationPortfolio.familyId,
                ),
                configuration = ConfigurationReport(
                    optimization = optimizationConfig,
                    timeout = timeout.toString(),
                    environment = environment.entries
                        .toSortedMap()
                        .entries
                        .joinToString(", ") { "${it.key}=${it.value}" },
                ),
                verification = VerificationOutcomeReport(
                    verdict = result.verdict,
                    message = result.message,
                    metadata = result.metadata,
                    metrics = result.metrics,
                ),
                artifacts = artifactPointersFor(caseArtifactPath, result),
            )
            reportPath.writeText(json.encodeToString(VerificationReport.serializer(), report))
            logger.info { "[${case.qualifiedName}] wrote verification report to $reportPath" }
        } catch (e: Exception) {
            logger.warn("[${case.qualifiedName}] failed to write report at $reportPath: ${e.message ?: e::class.simpleName}", e)
        }
    }

    private fun artifactPointersFor(caseArtifactPath: Path, result: VerificationResult): ArtifactPointersReport {
        fun relIfExists(name: String): String? {
            val path = caseArtifactPath.resolve(name)
            return if (path.toFile().exists()) name else null
        }
        val backendDirs = caseArtifactPath.toFile()
            .listFiles { file -> file.isDirectory && file.name != "pipeline" }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
        return ArtifactPointersReport(
            inputModel = relIfExists("inlined.oxsts"),
            instantiatedModel = relIfExists("pipeline/instantiated.oxsts"),
            inlinedModel = relIfExists("pipeline/inlined.oxsts"),
            flattenedModel = relIfExists("pipeline/flattened.oxsts"),
            witness = if (result.verificationTrace is VerificationTrace.OxstsWitness) relIfExists("witness.oxsts") else null,
            trace = if (result.verificationTrace is VerificationTrace.OxstsWitness) relIfExists("trace.json") else null,
            mapping = relIfExists("mapping.json"),
            backendDirectories = backendDirs,
        )
    }

    private fun backannotateResult(
        backendResult: BackendVerificationResult,
        compilation: FlattenedCompilationContext,
        qualifiedName: String,
        caseArtifactPath: Path,
    ): VerificationResult {
        val witness = backendResult.witness
        if (witness == null) {
            return VerificationResult(
                verdict = backendResult.verdict,
                metadata = backendResult.metadata,
                metrics = backendResult.metrics,
                verificationTrace = VerificationTrace.NoTrace,
                message = backendResult.message,
            )
        }

        logger.info { "[$qualifiedName] back-annotating inlined witness from ${backendResult.metadata.backendId}" }
        val (trace, duration) = measureTimedValue {
            val classWitness = classWitnessTransformer.transform(witness, compilation)
            val backAnnotatedWitness = witnessBackAnnotator.createWitnessInlinedOxsts(
                classWitness,
                backendResult.verdict,
            )
            val callTrace = callTraceTransformer.transformWitness(classWitness, compilation.transitionCallTraces)
            VerificationTrace.OxstsWitness(classWitness, backAnnotatedWitness, callTrace)
        }
        logger.info { "[$qualifiedName] back-annotation completed in $duration" }

        serializeArtifacts(trace, caseArtifactPath, qualifiedName)

        return VerificationResult(
            verdict = backendResult.verdict,
            metadata = backendResult.metadata,
            metrics = backendResult.metrics.copy(
                backAnnotationDuration = backendResult.metrics.backAnnotationDuration + duration,
                totalDuration = backendResult.metrics.totalDuration + duration,
            ),
            verificationTrace = trace,
            message = backendResult.message,
        )
    }

    private fun serializeArtifacts(
        trace: VerificationTrace.OxstsWitness,
        caseArtifactPath: Path,
        qualifiedName: String,
    ) {
        if (artifactConfig.isEnabled(ArtifactKind.Witness)) {
            serializeWitnessArtifact(caseArtifactPath, trace, qualifiedName)
        }
        if (artifactConfig.isEnabled(ArtifactKind.Trace)) {
            serializeTraceArtifact(caseArtifactPath, trace, qualifiedName)
        }
    }

    private fun serializeWitnessArtifact(
        caseArtifactPath: Path,
        trace: VerificationTrace.OxstsWitness,
        qualifiedName: String,
    ) {
        val witnessPath = caseArtifactPath.resolve("witness.oxsts")
        try {
            witnessPath.parent?.createDirectories()
            val witness = trace.backAnnotatedWitness
            val resourceSet = witness.classDeclaration.eResource().resourceSet
            val witnessUri = URI.createFileURI(witnessPath.toAbsolutePath().toString())
            resourceSet.getResource(witnessUri, false)?.delete(emptyMap<Any, Any>())
            resourceSet.createResource(witnessUri).contents += witness
            witnessPath.bufferedWriter().use { writer ->
                oxstsSerializer.serialize(witness, writer, SaveOptions.defaultOptions())
            }
            logger.info { "[$qualifiedName] wrote witness artifact to $witnessPath" }
        } catch (e: Exception) {
            logger.warn("[$qualifiedName] failed to write witness artifact at $witnessPath: ${e.message ?: e::class.simpleName}", e)
        }
    }

    private fun serializeTraceArtifact(
        caseArtifactPath: Path,
        trace: VerificationTrace.OxstsWitness,
        qualifiedName: String,
    ) {
        val tracePath = caseArtifactPath.resolve("trace.json")
        try {
            tracePath.parent?.createDirectories()
            tracePath.writeText(json.encodeToString(trace.callTrace))
            logger.info { "[$qualifiedName] wrote call trace artifact to $tracePath" }
        } catch (e: Exception) {
            logger.warn("[$qualifiedName] failed to write trace artifact at $tracePath: ${e.message ?: e::class.simpleName}", e)
        }
    }

    private suspend inline fun <T> runInTimeout(
        progress: ProgressContext,
        crossinline block: suspend () -> T,
    ): T = coroutineScope {
        val mainWork = async {
            withTimeout(timeout) {
                block()
            }
        }
        val cancelPoller = launch {
            while (isActive) {
                delay(CANCEL_POLL_INTERVAL)
                try {
                    progress.checkIsCancelled()
                } catch (c: CancellationException) {
                    mainWork.cancel(c)
                    return@launch
                }
            }
        }
        try {
            mainWork.await()
        } finally {
            cancelPoller.cancel()
        }
    }

    override fun close() {
        compiler.close()
    }

    private fun isTriviallyVerifiable(inlinedOxsts: InlinedOxsts): Boolean {
        return inlinedOxsts.variables.isEmpty() && inlinedOxsts.initTransition.isEmpty() && inlinedOxsts.mainTransition.isEmpty()
    }

    private fun TransitionDeclaration.isEmpty(): Boolean {
        if (branches.size > 1) {
            return false
        }
        val branch = branches.single()
        return branch.steps.isEmpty()
    }

    private fun trivialVerdict(
        qualifiedName: String,
        inlinedOxsts: InlinedOxsts,
        compilation: FlattenedCompilationContext,
        caseArtifactPath: Path,
    ): VerificationResult {
        val body = when (val expression = inlinedOxsts.property.expression) {
            is AG -> expression.body
            is EF -> expression.body
            else -> error("Specified property is not in the expected form!")
        }

        val evaluation = constantEvaluatorProvider.evaluate(body)
        require(evaluation is BooleanEvaluation) {
            "Trivially verifiable model's property body did not evaluate to a boolean (got ${evaluation::class.simpleName})"
        }

        val verdict = if (evaluation.value()) {
            VerificationVerdict.Passed
        } else {
            VerificationVerdict.Failed
        }

        logger.info { "Trivial $verdict verdict (model optimized away)." }
        val backendResult = BackendVerificationResult(
            verdict = verdict,
            metadata = VerificationRunMetadata(
                backendId = "trivial",
                startedAt = Clock.System.now(),
                caseQualifiedName = qualifiedName,
            ),
            metrics = VerificationMetrics(),
            witness = trivialWitness(inlinedOxsts),
            message = "Property decided at compile time.",
        )
        return backannotateResult(backendResult, compilation, qualifiedName, caseArtifactPath)
    }

    private fun trivialWitness(inlinedOxsts: InlinedOxsts): InlinedOxstsAssumptionWitness {
        val initialState = InlinedOxstsAssumptionWitnessState(values = emptyList(), activatedTraces = emptyList())
        val initializedState = InlinedOxstsAssumptionWitnessState(values = emptyList(), activatedTraces = emptyList())
        val transitionState = InlinedOxstsAssumptionWitnessState(values = emptyList(), activatedTraces = emptyList())
        return InlinedOxstsAssumptionWitness(
            initialState = initialState,
            initializedState = initializedState,
            transitionStates = listOf(transitionState),
            nextStateMap = mapOf(initializedState to listOf(transitionState)),
            inlinedOxsts = inlinedOxsts,
        )
    }

}
