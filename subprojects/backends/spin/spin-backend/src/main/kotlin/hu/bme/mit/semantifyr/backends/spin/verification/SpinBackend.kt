/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.verification

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
import hu.bme.mit.semantifyr.backends.spin.SpinExecutionSpecification
import hu.bme.mit.semantifyr.backends.spin.SpinExecutor
import hu.bme.mit.semantifyr.backends.spin.SpinExecutorSpec
import hu.bme.mit.semantifyr.backends.spin.SpinReplaySpecification
import hu.bme.mit.semantifyr.backends.spin.artifacts.SpinArtifactManager
import hu.bme.mit.semantifyr.backends.spin.execution.ShellBasedSpinExecutor
import hu.bme.mit.semantifyr.backends.spin.trace.SpinInlinedOxstsWitnessTransformer
import hu.bme.mit.semantifyr.backends.spin.trace.SpinTraceParser
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinArtifacts
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinModelGenerator
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.scopes.Seed
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object SpinBackend : VerificationBackend<SpinConfig>() {
    override val id: String = "spin"

    private val logger by loggerFactory()

    private val injector = OxstsStandaloneSetup()
        .createInjectorAndDoEMFRegistration()
        .createChildInjector(SpinBackendModule)

    override fun probeAvailability(
        config: SpinConfig,
        environment: ExecutionEnvironment,
    ): AvailabilityReport {
        val available = ShellBasedSpinExecutor().isAvailable()
        return if (available) {
            AvailabilityReport.Available
        } else {
            AvailabilityReport.Unavailable(
                reason = "spin not on PATH",
                hints = listOf(
                    "Install Spin from https://spinroot.com/spin/Src/ and make sure both 'spin' and a C compiler (gcc) are on PATH.",
                ),
            )
        }
    }

    override suspend fun verify(
        config: SpinConfig,
        request: VerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        val executorSpec = environment.spin ?: SpinExecutorSpec.Auto
        return withSpinVerificationScope(request, config, executorSpec) {
            injector.getInstance(SpinVerificationContext::class.java).execute()
        }
    }
}

val ExecutionEnvironment.spin: SpinExecutorSpec?
    get() = entries["spin"] as? SpinExecutorSpec

fun ExecutionEnvironment.Builder.spin(spec: SpinExecutorSpec): ExecutionEnvironment.Builder {
    return put("spin", spec)
}

var Seed.spinConfig: SpinConfig
    get() = error("Seed slots are write-only; read seeded values via injection inside the scope.")
    set(value) {
        seed(Key.get(SpinConfig::class.java), value)
    }

var Seed.spinExecutorSpec: SpinExecutorSpec
    get() = error("Seed slots are write-only; read seeded values via injection inside the scope.")
    set(value) {
        seed(Key.get(SpinExecutorSpec::class.java), value)
    }

suspend fun <T> withSpinVerificationScope(
    request: VerificationRequest,
    config: SpinConfig,
    executorSpec: SpinExecutorSpec,
    block: suspend () -> T,
): T {
    return withVerificationScope(
        Seed().apply {
            this.verificationRequest = request
            this.spinConfig = config
            this.spinExecutorSpec = executorSpec
        },
        block,
    )
}

internal object SpinBackendModule : AbstractModule() {
    override fun configure() {
        bindScope(VerificationScoped::class.java, VerificationScope)

        bind(VerificationRequest::class.java)
            .toProvider(Provider { error("VerificationRequest must be seeded into the verification scope") })
            .`in`(VerificationScope)
        bind(SpinConfig::class.java)
            .toProvider(Provider { error("SpinConfig must be seeded into the verification scope") })
            .`in`(VerificationScope)
        bind(SpinExecutorSpec::class.java)
            .toProvider(Provider { error("SpinExecutorSpec must be seeded into the verification scope") })
            .`in`(VerificationScope)
    }

    @Provides
    @VerificationScoped
    fun provideSpinArtifactManager(request: VerificationRequest): SpinArtifactManager {
        return SpinArtifactManager(request.artifactOutputPath)
    }
}

@VerificationScoped
internal class SpinVerificationContext @Inject constructor(
    private val config: SpinConfig,
    private val executorSpec: SpinExecutorSpec,
    request: VerificationRequest,
    private val artifactManager: SpinArtifactManager,
    private val spinModelGenerator: SpinModelGenerator,
    private val traceParser: SpinTraceParser,
    private val witnessTransformer: SpinInlinedOxstsWitnessTransformer,
) : VerificationContextBase(backendId = "spin:${config.id}", request = request) {
    private val logger by loggerFactory()
    private val configId = backendId

    override suspend fun runVerification(
        metadata: VerificationRunMetadata,
        totalMark: TimeSource.Monotonic.ValueTimeMark,
    ): BackendVerificationResult {
        val (artifacts, preparationDuration) = measureTimedValue { prepare() }
        logger.info { "[$configId] OXSTS -> Promela prepared in $preparationDuration" }

        val (rawVerdict, verificationDuration) = measureTimedValue { runSpin() }
        logger.info { "[$configId] spin returned $rawVerdict in $verificationDuration" }

        val verdict = if (artifacts.property.invertVerdict) rawVerdict?.invert() else rawVerdict
        val interpreted = interpretVerdict(verdict)
        logger.info { "[$configId] verdict $interpreted for '${request.case.qualifiedName}' (total ${totalMark.elapsedNow()})" }

        var witness: InlinedOxstsAssumptionWitness? = null
        var backAnnotationDuration = Duration.ZERO
        if (rawVerdict == SpinVerdict.False && artifactManager.trailFile.exists()) {
            backAnnotationDuration = measureTime {
                witness = buildWitness()
            }
        }

        return BackendVerificationResult(
            verdict = interpreted,
            metadata = metadata,
            metrics = VerificationMetrics(
                preparationDuration = preparationDuration,
                verificationDuration = verificationDuration,
                backAnnotationDuration = backAnnotationDuration,
                totalDuration = totalMark.elapsedNow(),
            ),
            witness = witness,
            message = null,
        )
    }

    private suspend fun buildWitness(): InlinedOxstsAssumptionWitness? {
        val executor = SpinExecutor.of(executorSpec)
        val replaySpec = SpinReplaySpecification(
            workingDirectory = artifactManager.modelFile.parentFile,
            modelFileName = artifactManager.modelFile.name,
            logFile = artifactManager.replayLogFile,
            errorFile = artifactManager.errorFile,
        )
        val replayResult = executor.replayTrail(replaySpec)
        if (replayResult.exitCode != 0 && replayResult.exitCode != 1) {
            logger.warn { "[$configId] spin replay failed with exit code ${replayResult.exitCode}; witness not produced" }
            return null
        }
        val trace = traceParser.parse(artifactManager.replayLogFile)
        if (trace == null) {
            logger.warn { "[$configId] no parseable steps in spin replay; witness not produced" }
            return null
        }
        logger.info { "[$configId] parsed Spin trace with ${trace.states.size} state(s); building witness" }
        return witnessTransformer.transform(request.input, trace)
    }

    private fun prepare(): SpinArtifacts {
        logger.info { "[$configId] generating Promela model" }
        val artifacts = spinModelGenerator.generate(request.input)
        artifactManager.modelFile.apply { parentFile?.mkdirs() }.writeText(artifacts.promela)
        return artifacts
    }

    private suspend fun runSpin(): SpinVerdict? {
        val executor = SpinExecutor.of(executorSpec)
        val spec = SpinExecutionSpecification(
            workingDirectory = artifactManager.modelFile.parentFile,
            modelFileName = artifactManager.modelFile.name,
            extraArguments = config.extraArguments,
            logFile = artifactManager.logFile,
            errorFile = artifactManager.errorFile,
        )

        val result = executor.execute(spec)

        // Spin returns 1 on counterexample; treat non-crash exits as valid and rely on output parsing.
        if (result.exitCode != 0 && result.exitCode != 1) {
            error("spin execution failed with exit code ${result.exitCode}")
        }

        return artifactManager.logFile
            .takeIf { it.exists() }
            ?.useLines { lines -> detectVerdict(lines) }
    }

    private fun detectVerdict(lines: Sequence<String>): SpinVerdict? {
        var explicitFailure = false
        var explicitSuccess = false
        for (raw in lines) {
            val line = raw.trim()
            val lower = line.lowercase()
            // Counterexample signals - either LTL claim violated or liveness acceptance cycle found.
            if ("violated" in lower || "acceptance cycle" in lower || "assertion violated" in lower) {
                explicitFailure = true
            }
            // Spin's summary line after a clean run. Reliable when combined with absence of failure signals.
            if (lower.contains("errors: 0")) {
                explicitSuccess = true
            }
        }
        return when {
            explicitFailure -> SpinVerdict.False
            explicitSuccess -> SpinVerdict.True
            else -> null
        }
    }

    private fun interpretVerdict(verdict: SpinVerdict?): VerificationVerdict {
        return when (verdict) {
            SpinVerdict.True -> VerificationVerdict.Passed
            SpinVerdict.False -> VerificationVerdict.Failed
            null -> VerificationVerdict.Inconclusive
        }
    }

    private enum class SpinVerdict {
        True,
        False;

        fun invert(): SpinVerdict = if (this == True) False else True
    }
}
