/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import com.google.inject.AbstractModule
import com.google.inject.Inject
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.Provides
import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationContextBase
import hu.bme.mit.semantifyr.backend.VerificationMetrics
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.scopes.VerificationScope
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.scopes.verificationRequest
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitness
import hu.bme.mit.semantifyr.backends.theta.artifacts.ThetaArtifactManager
import hu.bme.mit.semantifyr.backends.theta.backannotation.CexReader
import hu.bme.mit.semantifyr.backends.theta.backannotation.witness.cex.CexAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.witness.oxsts.InlinedOxstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.witness.xsts.XstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.execution.DockerBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.backends.theta.execution.ShellBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.OxstsTransformer
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.scopes.Seed
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object ThetaBackend : VerificationBackend<ThetaConfig>() {

    override val id: String = "theta"

    private val logger by loggerFactory()

    private val injector = OxstsStandaloneSetup()
        .createInjectorAndDoEMFRegistration()
        .createChildInjector(ThetaBackendModule)

    override fun probeAvailability(config: ThetaConfig, environment: ExecutionEnvironment): AvailabilityReport {
        val executorSpec = environment.theta ?: ThetaExecutorSpec.Auto
        logger.debug { "[theta] probing availability for executor spec $executorSpec" }
        val report = probeExecutor(executorSpec)
        logger.debug { "[theta] availability: $report" }
        return report
    }

    override suspend fun verify(
        config: ThetaConfig,
        request: VerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        val executorSpec = environment.theta ?: ThetaExecutorSpec.Auto
        logger.info { "[theta:${config.id}] dispatching '${request.case.qualifiedName}' via $executorSpec" }
        return withThetaVerificationScope(request, config, executorSpec) {
            injector.getInstance(ThetaVerificationContext::class.java).execute()
        }
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
        if (shell == AvailabilityReport.Available) {
            logger.debug { "[theta] auto: shell probe available; selecting theta-xsts-cli on PATH" }
            return AvailabilityReport.Available
        }
        val docker = probeExecutor(ThetaExecutorSpec.Docker())
        if (docker == AvailabilityReport.Available) {
            logger.info { "[theta] auto: theta-xsts-cli not on PATH; falling back to Docker" }
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

var Seed.thetaConfig: ThetaConfig
    get() = error("Seed slots are write-only; read seeded values via injection inside the scope.")
    set(value) {
        seed(Key.get(ThetaConfig::class.java), value)
    }

var Seed.thetaExecutorSpec: ThetaExecutorSpec
    get() = error("Seed slots are write-only; read seeded values via injection inside the scope.")
    set(value) {
        seed(Key.get(ThetaExecutorSpec::class.java), value)
    }

suspend fun <T> withThetaVerificationScope(
    request: VerificationRequest,
    config: ThetaConfig,
    executorSpec: ThetaExecutorSpec,
    block: suspend () -> T,
): T {
    return withVerificationScope(
        Seed().apply {
            this.verificationRequest = request
            this.thetaConfig = config
            this.thetaExecutorSpec = executorSpec
        },
        block,
    )
}

private object ThetaBackendModule : AbstractModule() {
    override fun configure() {
        bindScope(VerificationScoped::class.java, VerificationScope)

        bind(VerificationRequest::class.java)
            .toProvider(Provider { error("VerificationRequest must be seeded into the verification scope") })
            .`in`(VerificationScope)
        bind(ThetaConfig::class.java)
            .toProvider(Provider { error("ThetaConfig must be seeded into the verification scope") })
            .`in`(VerificationScope)
        bind(ThetaExecutorSpec::class.java)
            .toProvider(Provider { error("ThetaExecutorSpec must be seeded into the verification scope") })
            .`in`(VerificationScope)
    }

    @Provides
    @VerificationScoped
    fun provideThetaArtifactManager(request: VerificationRequest): ThetaArtifactManager {
        return ThetaArtifactManager(request.artifactOutputPath)
    }
}

@VerificationScoped
internal class ThetaVerificationContext @Inject constructor(
    private val config: ThetaConfig,
    private val executorSpec: ThetaExecutorSpec,
    request: VerificationRequest,
    private val artifactManager: ThetaArtifactManager,
    private val oxstsTransformer: OxstsTransformer,
    private val cexReader: CexReader,
    private val cexAssumptionWitnessTransformer: CexAssumptionWitnessTransformer,
    private val xstsAssumptionWitnessTransformer: XstsAssumptionWitnessTransformer,
    private val inlinedOxstsAssumptionWitnessTransformer: InlinedOxstsAssumptionWitnessTransformer,
) : VerificationContextBase(backendId = "theta:${config.id}", request = request) {

    private val logger by loggerFactory()
    private val configId = backendId

    override suspend fun runVerification(
        metadata: VerificationRunMetadata,
        totalMark: TimeSource.Monotonic.ValueTimeMark,
    ): BackendVerificationResult {
        val (xsts, transformDuration) = measureTimedValue { transformToXsts() }
        logger.info { "[$configId] OXSTS -> XSTS transform took $transformDuration" }

        val executionSpec = buildExecutionSpec()

        val (thetaVerdict, verifyDuration) = measureTimedValue { runTheta(executionSpec) }
        logger.info { "[$configId] Theta returned $thetaVerdict in $verifyDuration" }

        var witness: InlinedOxstsAssumptionWitness? = null
        var backAnnotationDuration = Duration.ZERO
        if (artifactManager.cexFile.exists()) {
            backAnnotationDuration = measureTime {
                witness = buildInlinedWitness(xsts, artifactManager.cexFile.toPath())
            }
        }

        val verdict = interpretVerdict(request.input.property.expression, thetaVerdict)
        logger.info { "[$configId] verdict $verdict for '${request.case.qualifiedName}' (total ${totalMark.elapsedNow()})" }

        return BackendVerificationResult(
            verdict = verdict,
            metadata = metadata,
            metrics = VerificationMetrics(
                preparationDuration = transformDuration,
                verificationDuration = verifyDuration,
                backAnnotationDuration = backAnnotationDuration,
                totalDuration = totalMark.elapsedNow(),
            ),
            witness = witness,
            message = null,
        )
    }

    private fun transformToXsts(): XstsModel {
        logger.info { "[$configId] transforming InlinedOxsts -> XSTS" }
        val xsts = oxstsTransformer.transform(request.input)

        val resourceSet = request.input.eResource().resourceSet
        resourceSet.getResource(artifactManager.xstsUri, false)?.delete(emptyMap<Any, Any>())
        val resource = resourceSet.createResource(artifactManager.xstsUri)
        resource.contents += xsts
        xsts.eResource().save(emptyMap<Any, Any>())

        return xsts
    }

    private fun buildExecutionSpec(): ThetaExecutionSpecification {
        val workingDir = artifactManager.xstsFile.parentFile
        val cexRelPath = artifactManager.cexFile.relativeTo(workingDir).path
        val command = config.parameters.split(" ") + listOf("--model", artifactManager.xstsFile.name, "--cexfile", cexRelPath)

        return ThetaExecutionSpecification(
            workingDirectory = workingDir,
            command = command,
            logFile = artifactManager.logFile,
            errorFile = artifactManager.errorFile,
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

    private fun buildInlinedWitness(xsts: XstsModel, cexPath: Path): InlinedOxstsAssumptionWitness {
        logger.info { "[$configId] building inlined-oxsts witness from CEX" }
        val cexModel = cexReader.loadCexModel(cexPath)
        val cexWitness = cexAssumptionWitnessTransformer.transform(cexModel)
        val xstsWitness = xstsAssumptionWitnessTransformer.transform(xsts, cexWitness)
        return inlinedOxstsAssumptionWitnessTransformer.transform(request.input, xstsWitness)
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
