/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.hu.bme.mit.semantifyr.verification

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import com.google.inject.assistedinject.FactoryModuleBuilder
import hu.bme.mit.semantifyr.backends.theta.artifacts.ThetaArtifactManager
import hu.bme.mit.semantifyr.backends.theta.backannotation.CexReader
import hu.bme.mit.semantifyr.backends.theta.backannotation.witness.cex.CexAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.witness.oxsts.InlinedOxstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.witness.xsts.XstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.OxstsTransformer
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionSpecification
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorSpec
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutor
import hu.bme.mit.semantifyr.backends.theta.execution.DockerBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.backends.theta.execution.ShellBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationMetrics
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.backend.VerificationTrace
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import org.eclipse.emf.common.util.URI
import java.io.File
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.markNow
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object ThetaBackend : VerificationBackend<hu.bme.mit.semantifyr.backends.theta.hu.bme.mit.semantifyr.verification.ThetaConfig> {

    override val id: String = "theta"

    private val logger by loggerFactory()

    private fun createInjector(artifactRootPath: Path): Injector {
        // TODO: shared InlinedOxsts EObjects may still race on eAdapters during parallel transforms.
        val parentInjector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        return parentInjector.createChildInjector(ThetaBackendModule(artifactRootPath))
    }

    override fun probeAvailability(config: ThetaConfig, environment: ExecutionEnvironment): AvailabilityReport {
        return probeExecutor(environment.theta ?: ThetaExecutorSpec.Auto)
    }

    override suspend fun verify(config: ThetaConfig, request: VerificationRequest, environment: ExecutionEnvironment): VerificationResult {
        val executorSpec = environment.theta ?: ThetaExecutorSpec.Auto
        val injector = createInjector(request.artifactOutputPath)
        val factory = injector.getInstance(ThetaVerificationContext.Factory::class.java)
        val context = factory.create(config, executorSpec, request)
        return context.execute()
    }

    private fun probeExecutor(executor: ThetaExecutorSpec): AvailabilityReport {
        return when (executor) {
            ThetaExecutorSpec.Auto -> probeAuto()
            ThetaExecutorSpec.Shell -> if (ShellBasedThetaXstsExecutor().isAvailable()) {
                AvailabilityReport.Available
            } else {
                AvailabilityReport.Unavailable(reason = "theta-xsts-cli not on PATH", hints = listOf("Install theta-xsts-cli and ensure it is on PATH."))
            }
            is ThetaExecutorSpec.Docker -> if (DockerBasedThetaXstsExecutor(executor.image).isAvailable()) {
                AvailabilityReport.Available
            } else {
                AvailabilityReport.Unavailable(reason = "Docker CLI not found on PATH", hints = listOf("Install Docker and ensure the daemon is running.", "Pull the image manually with: docker pull ${executor.image}"))
            }
        }
    }

    private fun probeAuto(): AvailabilityReport {
        val shell = probeExecutor(ThetaExecutorSpec.Shell)
        if (shell == AvailabilityReport.Available) return AvailabilityReport.Available
        val docker = probeExecutor(ThetaExecutorSpec.Docker())
        if (docker == AvailabilityReport.Available) {
            return AvailabilityReport.Degraded("theta-xsts-cli not on PATH; falling back to the ftsrg/theta-xsts-cli Docker image (requires a running Docker daemon at verify time).")
        }
        return AvailabilityReport.Unavailable(reason = "neither theta-xsts-cli nor Docker found on PATH", hints = listOf("Install theta-xsts-cli and ensure it is on PATH (preferred).", "Alternatively, install Docker and pull ftsrg/theta-xsts-cli."))
    }
}

val ExecutionEnvironment.theta: ThetaExecutorSpec?
    get() = entries["theta"] as? ThetaExecutorSpec

fun ExecutionEnvironment.Builder.theta(spec: ThetaExecutorSpec): ExecutionEnvironment.Builder {
    return put("theta", spec)
}

private class ThetaBackendModule(private val artifactRootPath: Path) : AbstractModule() {
    override fun configure() {
        bind(ThetaArtifactManager::class.java).toInstance(ThetaArtifactManager(artifactRootPath))
        install(FactoryModuleBuilder().build(ThetaVerificationContext.Factory::class.java))
    }
}

internal class ThetaVerificationContext @AssistedInject constructor(
    @param:Assisted private val config: ThetaConfig,
    @param:Assisted private val executorSpec: ThetaExecutorSpec,
    @param:Assisted private val request: VerificationRequest,
    private val oxstsTransformer: OxstsTransformer,
    private val cexReader: CexReader,
    private val cexAssumptionWitnessTransformer: CexAssumptionWitnessTransformer,
    private val xstsAssumptionWitnessTransformer: XstsAssumptionWitnessTransformer,
    private val inlinedOxstsAssumptionWitnessTransformer: InlinedOxstsAssumptionWitnessTransformer,
) {
    private val logger by loggerFactory()
    private val configId = "theta:${config.id}"
    private val artifactDir: File get() = request.artifactOutputPath.toFile()
    private val xstsFile: File get() = artifactDir.resolve("inlined.xsts")
    private val xstsUri: URI get() = URI.createFileURI(xstsFile.absolutePath)

    interface Factory {
        fun create(config: ThetaConfig, executorSpec: ThetaExecutorSpec, request: VerificationRequest): ThetaVerificationContext
    }

    suspend fun execute(): VerificationResult {
        val metadata = VerificationRunMetadata(
            backendId = configId,
            startedAt = Clock.System.now(),
            caseQualifiedName = request.case.qualifiedName,
        )
        val totalMark = markNow()

        logger.info { "[$configId] starting verification of '${request.case.qualifiedName}'" }

        return try {
            runVerification(totalMark, metadata)
        } catch (c: CancellationException) {
            logger.debug { "[$configId] cancelled (peer won the portfolio race or outer timeout)" }
            throw c
        } catch (e: Exception) {
            logger.warn { "[$configId] verification of '${request.case.qualifiedName}' threw ${e::class.simpleName}: ${e.message ?: ""}" }
            VerificationResult(
                verdict = VerificationVerdict.Errored,
                metadata = metadata,
                metrics = VerificationMetrics(totalDuration = totalMark.elapsedNow()),
                message = e.message ?: e::class.simpleName,
            )
        }
    }

    private suspend fun runVerification(
        totalMark: TimeSource.Monotonic.ValueTimeMark,
        metadata: VerificationRunMetadata
    ): VerificationResult {
        val (xsts, transformDuration) = measureTimedValue { transformToXsts() }
        logger.info { "[$configId] OXSTS -> XSTS transform took $transformDuration" }

        val executionSpec = buildExecutionSpec()

        val (thetaVerdict, verifyDuration) = measureTimedValue { runTheta(executionSpec) }
        logger.info { "[$configId] Theta returned $thetaVerdict in $verifyDuration" }

        val cexFile = artifactDir.resolve("out.cex")
        var trace: VerificationTrace.OxstsWitness? = null
        var backAnnotationDuration = Duration.ZERO
        if (cexFile.exists()) {
            backAnnotationDuration = measureTime {
                trace = backAnnotateWitness(request.input, xsts, cexFile.toPath())
            }
        }

        val verdict = interpretVerdict(request.input.property.expression, thetaVerdict)
        logger.info { "[$configId] verdict $verdict for '${request.case.qualifiedName}' (total ${totalMark.elapsedNow()})" }

        return VerificationResult(
            verdict = verdict,
            metadata = metadata,
            metrics = VerificationMetrics(
                preparationDuration = transformDuration,
                verificationDuration = verifyDuration,
                backAnnotationDuration = backAnnotationDuration,
                totalDuration = totalMark.elapsedNow(),
            ),
            verificationTrace = trace,
            message = null,
        )
    }

    private fun transformToXsts(): XstsModel {
        logger.info { "[$configId] transforming InlinedOxsts -> XSTS" }
        val xsts = oxstsTransformer.transform(request.input)

        val resourceSet = request.input.eResource().resourceSet
        resourceSet.getResource(xstsUri, false)?.delete(mutableMapOf<Any, Any>())
        val resource = resourceSet.createResource(xstsUri)
        resource.contents += xsts
        xsts.eResource().save(emptyMap<Any, Any>())

        return xsts
    }

    private fun buildExecutionSpec(): ThetaExecutionSpecification {
        val workingDir = xstsFile.parentFile
        val cexRelPath = artifactDir.resolve("out.cex").relativeTo(workingDir).path
        val command = config.parameters.split(" ") + listOf("--model", xstsFile.name, "--cexfile", cexRelPath)

        return ThetaExecutionSpecification(
            workingDirectory = workingDir,
            command = command,
            logFile = artifactDir.resolve("theta.out"),
            errorFile = artifactDir.resolve("theta.err"),
        )
    }

    private suspend fun runTheta(spec: ThetaExecutionSpecification): ThetaVerdict? {
        logger.debug { "[$configId] invoking Theta: ${config.parameters}" }
        val thetaExecutor = ThetaXstsExecutor.of(executorSpec)

        val result = thetaExecutor.execute(spec)

        if (result.exitCode != 0) {
            error("Theta execution failed with exit code ${result.exitCode}")
        }

        return spec.logFile?.takeIf { it.exists() }?.useLines { lines -> lines.firstNotNullOfOrNull(::detectSafety) }
    }

    private fun backAnnotateWitness(input: InlinedOxsts, xsts: XstsModel, cexPath: Path): VerificationTrace.OxstsWitness {
        logger.info { "[$configId] back-annotating witness" }
        val cexModel = cexReader.loadCexModel(cexPath)
        val cexWitness = cexAssumptionWitnessTransformer.transform(cexModel)
        val xstsWitness = xstsAssumptionWitnessTransformer.transform(xsts, cexWitness)
        val inlinedWitness = inlinedOxstsAssumptionWitnessTransformer.transform(input, xstsWitness)
        return VerificationTrace.OxstsWitness(inlinedWitness)
    }

    private fun interpretVerdict(property: Expression, thetaVerdict: ThetaVerdict?): VerificationVerdict {
        return when {
            property is AG && thetaVerdict == ThetaVerdict.Unsafe -> VerificationVerdict.Failed
            property is EF && thetaVerdict == ThetaVerdict.Safe -> VerificationVerdict.Failed
            thetaVerdict == ThetaVerdict.Safe || thetaVerdict == ThetaVerdict.Unsafe -> VerificationVerdict.Passed
            else -> VerificationVerdict.Inconclusive
        }
    }

    private fun detectSafety(line: String): ThetaVerdict? = when {
        line.contains("SafetyResult Unsafe") -> ThetaVerdict.Unsafe
        line.contains("SafetyResult Safe") -> ThetaVerdict.Safe
        else -> null
    }

    private enum class ThetaVerdict { Safe, Unsafe }
}
