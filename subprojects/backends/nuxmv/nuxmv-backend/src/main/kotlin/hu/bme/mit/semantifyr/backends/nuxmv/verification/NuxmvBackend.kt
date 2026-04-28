/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.verification

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
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutionSpecification
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutor
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutorSpec
import hu.bme.mit.semantifyr.backends.nuxmv.artifacts.NuxmvArtifactManager
import hu.bme.mit.semantifyr.backends.nuxmv.execution.ShellBasedNuxmvExecutor
import hu.bme.mit.semantifyr.backends.nuxmv.trace.NuxmvInlinedOxstsWitnessTransformer
import hu.bme.mit.semantifyr.backends.nuxmv.trace.NuxmvTraceParser
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvArtifacts
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvModelGenerator
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.scopes.Seed
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object NuxmvBackend : VerificationBackend<NuxmvConfig>() {
    override val id: String = "nuxmv"

    private val logger by loggerFactory()

    private val injector = OxstsStandaloneSetup()
        .createInjectorAndDoEMFRegistration()
        .createChildInjector(NuxmvBackendModule)

    override fun probeAvailability(
        config: NuxmvConfig,
        environment: ExecutionEnvironment,
    ): AvailabilityReport {
        return if (ShellBasedNuxmvExecutor().isAvailable()) {
            AvailabilityReport.Available
        } else {
            AvailabilityReport.Unavailable(
                reason = "nuXmv not on PATH",
                hints = listOf(
                    "Download nuXmv from https://nuxmv.fbk.eu/download.html and add its bin folder to PATH.",
                ),
            )
        }
    }

    override suspend fun verify(
        config: NuxmvConfig,
        request: VerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        val executorSpec = environment.nuxmv ?: NuxmvExecutorSpec.Auto
        return withNuxmvVerificationScope(request, config, executorSpec) {
            injector.getInstance(NuxmvVerificationContext::class.java).execute()
        }
    }
}

val ExecutionEnvironment.nuxmv: NuxmvExecutorSpec?
    get() = entries["nuxmv"] as? NuxmvExecutorSpec

fun ExecutionEnvironment.Builder.nuxmv(spec: NuxmvExecutorSpec): ExecutionEnvironment.Builder {
    return put("nuxmv", spec)
}

var Seed.nuxmvConfig: NuxmvConfig
    get() = error("Seed slots are write-only; read seeded values via injection inside the scope.")
    set(value) {
        seed(Key.get(NuxmvConfig::class.java), value)
    }

var Seed.nuxmvExecutorSpec: NuxmvExecutorSpec
    get() = error("Seed slots are write-only; read seeded values via injection inside the scope.")
    set(value) {
        seed(Key.get(NuxmvExecutorSpec::class.java), value)
    }

suspend fun <T> withNuxmvVerificationScope(
    request: VerificationRequest,
    config: NuxmvConfig,
    executorSpec: NuxmvExecutorSpec,
    block: suspend () -> T,
): T {
    return withVerificationScope(
        Seed().apply {
            this.verificationRequest = request
            this.nuxmvConfig = config
            this.nuxmvExecutorSpec = executorSpec
        },
        block,
    )
}

internal object NuxmvBackendModule : AbstractModule() {
    override fun configure() {
        bindScope(VerificationScoped::class.java, VerificationScope)

        bind(VerificationRequest::class.java)
            .toProvider(Provider { error("VerificationRequest must be seeded into the verification scope") })
            .`in`(VerificationScope)
        bind(NuxmvConfig::class.java)
            .toProvider(Provider { error("NuxmvConfig must be seeded into the verification scope") })
            .`in`(VerificationScope)
        bind(NuxmvExecutorSpec::class.java)
            .toProvider(Provider { error("NuxmvExecutorSpec must be seeded into the verification scope") })
            .`in`(VerificationScope)
    }

    @Provides
    @VerificationScoped
    fun provideNuxmvArtifactManager(request: VerificationRequest): NuxmvArtifactManager {
        return NuxmvArtifactManager(request.artifactOutputPath)
    }
}

@VerificationScoped
internal class NuxmvVerificationContext @Inject constructor(
    private val config: NuxmvConfig,
    private val executorSpec: NuxmvExecutorSpec,
    request: VerificationRequest,
    private val artifactManager: NuxmvArtifactManager,
    private val nuxmvModelGenerator: NuxmvModelGenerator,
    private val traceParser: NuxmvTraceParser,
    private val witnessTransformer: NuxmvInlinedOxstsWitnessTransformer,
) : VerificationContextBase(backendId = "nuxmv:${config.id}", request = request) {
    private val logger by loggerFactory()
    private val configId = backendId

    override suspend fun runVerification(
        metadata: VerificationRunMetadata,
        totalMark: TimeSource.Monotonic.ValueTimeMark,
    ): BackendVerificationResult {
        val (artifacts, preparationDuration) = measureTimedValue { prepare() }
        logger.info { "[$configId] OXSTS -> nuXmv SMV prepared in $preparationDuration" }

        val (rawVerdict, verificationDuration) = measureTimedValue { runNuxmv() }
        logger.info { "[$configId] nuXmv returned $rawVerdict in $verificationDuration" }

        val verdict = if (artifacts.property.invertVerdict) rawVerdict?.invert() else rawVerdict
        val interpreted = interpretVerdict(verdict)
        logger.info { "[$configId] verdict $interpreted for '${request.case.qualifiedName}' (total ${totalMark.elapsedNow()})" }

        // nuXmv only emits a trace when its raw invariant check returned False (an example
        // execution sequence is then dumped). For the True case there is no trace by design.
        var witness: InlinedOxstsAssumptionWitness? = null
        var backAnnotationDuration = Duration.ZERO
        if (rawVerdict == NuxmvVerdict.False) {
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

    private fun buildWitness(): InlinedOxstsAssumptionWitness? {
        val trace = traceParser.parse(artifactManager.logFile)
        if (trace == null) {
            logger.warn { "[$configId] no trace parseable from ${artifactManager.logFile.name}; witness will be omitted" }
            return null
        }
        logger.info { "[$configId] parsed nuXmv trace with ${trace.states.size} state(s); building witness" }
        return witnessTransformer.transform(request.input, trace)
    }

    private fun prepare(): NuxmvArtifacts {
        logger.info { "[$configId] generating SMV model and command files" }
        val artifacts = nuxmvModelGenerator.generate(request.input)

        artifactManager.modelFile.apply { parentFile?.mkdirs() }.writeText(artifacts.smv)

        val checkCommand = buildCheckCommand(artifacts)
        val cmdScript = buildString {
            appendLine("set on_failure_script_quits")
            appendLine("set input_file \"${artifactManager.modelFile.absolutePath}\"")
            appendLine(config.setupCommand)
            appendLine("set default_trace_plugin 1")
            appendLine(checkCommand)
            appendLine("quit")
        }
        artifactManager.commandFile.writeText(cmdScript)

        return artifacts
    }

    private fun buildCheckCommand(artifacts: NuxmvArtifacts): String {
        // The property transformer always yields an invariant, so every configured checkCommand
        // (IC3, BMC, or plain BDD-based check_invar) is invariant-based.
        return "${config.checkCommand} \"${artifacts.property.invariant}\""
    }

    private suspend fun runNuxmv(): NuxmvVerdict? {
        val executor = NuxmvExecutor.of(executorSpec)
        val spec = NuxmvExecutionSpecification(
            workingDirectory = artifactManager.modelFile.parentFile,
            commandFile = artifactManager.commandFile,
            logFile = artifactManager.logFile,
            errorFile = artifactManager.errorFile,
        )

        val result = executor.execute(spec)

        if (result.exitCode != 0 && result.exitCode != 1) {
            error("nuXmv execution failed with exit code ${result.exitCode}")
        }

        return artifactManager.logFile
            .takeIf { it.exists() }
            ?.useLines { lines -> detectVerdict(lines) }
    }

    private fun interpretVerdict(verdict: NuxmvVerdict?): VerificationVerdict {
        return when (verdict) {
            NuxmvVerdict.True -> VerificationVerdict.Passed
            NuxmvVerdict.False -> VerificationVerdict.Failed
            null -> VerificationVerdict.Inconclusive
        }
    }

    // nuXmv reports the verdict either as a single line (`-- invariant foo is true`) or, for
    // case-expression properties, as a multi-line block opened by `-- invariant ...` and
    // closed by a line ending in `is true` / `is false`. Walk the lines statefully so the
    // multi-line form is recognised; otherwise the verdict is silently lost.
    private fun detectVerdict(lines: Sequence<String>): NuxmvVerdict? {
        var inBlock = false
        for (raw in lines) {
            val lower = raw.lowercase()
            val opensBlock = lower.trimStart().startsWith("-- invariant") ||
                lower.trimStart().startsWith("-- specification")
            if (opensBlock) {
                if (lower.trimEnd().endsWith("is true")) {
                    return NuxmvVerdict.True
                }
                if (lower.trimEnd().endsWith("is false")) {
                    return NuxmvVerdict.False
                }
                inBlock = true
                continue
            }
            if (inBlock) {
                val trimmed = lower.trimEnd()
                if (trimmed.endsWith("is true")) {
                    return NuxmvVerdict.True
                }
                if (trimmed.endsWith("is false")) {
                    return NuxmvVerdict.False
                }
            }
        }
        return null
    }

    private enum class NuxmvVerdict {
        True,
        False,
        ;

        fun invert(): NuxmvVerdict = if (this == True) False else True
    }
}
