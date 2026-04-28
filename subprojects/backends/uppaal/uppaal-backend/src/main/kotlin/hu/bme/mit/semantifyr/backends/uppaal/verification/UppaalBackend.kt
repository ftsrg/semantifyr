/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.verification

import com.google.inject.AbstractModule
import com.google.inject.Inject
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.Provides
import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
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
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutionSpecification
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutor
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutorSpec
import hu.bme.mit.semantifyr.backends.uppaal.artifacts.UppaalArtifactManager
import hu.bme.mit.semantifyr.backends.uppaal.execution.ShellBasedUppaalExecutor
import hu.bme.mit.semantifyr.backends.uppaal.trace.UppaalInlinedOxstsWitnessTransformer
import hu.bme.mit.semantifyr.backends.uppaal.trace.UppaalTraceParser
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalArtifacts
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalModelGenerator
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.scopes.Seed
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object UppaalBackend : VerificationBackend<UppaalConfig>() {
    override val id: String = "uppaal"

    private val logger by loggerFactory()

    private val injector = OxstsStandaloneSetup()
        .createInjectorAndDoEMFRegistration()
        .createChildInjector(UppaalBackendModule)

    override fun probeAvailability(
        config: UppaalConfig,
        environment: ExecutionEnvironment,
    ): AvailabilityReport {
        return if (ShellBasedUppaalExecutor().isAvailable()) {
            AvailabilityReport.Available
        } else {
            AvailabilityReport.Unavailable(
                reason = "verifyta not on PATH",
                hints = listOf(
                    "Install Uppaal (https://uppaal.org) and ensure the 'verifyta' binary is on PATH.",
                    "On a typical Uppaal install the binary is under <uppaal>/bin-Linux/verifyta.",
                ),
            )
        }
    }

    override suspend fun verify(
        config: UppaalConfig,
        request: VerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        val executorSpec = environment.uppaal ?: UppaalExecutorSpec.Auto
        return withUppaalVerificationScope(request, config, executorSpec) {
            injector.getInstance(UppaalVerificationContext::class.java).execute()
        }
    }
}

val ExecutionEnvironment.uppaal: UppaalExecutorSpec?
    get() = entries["uppaal"] as? UppaalExecutorSpec

fun ExecutionEnvironment.Builder.uppaal(spec: UppaalExecutorSpec): ExecutionEnvironment.Builder {
    return put("uppaal", spec)
}

var Seed.uppaalConfig: UppaalConfig
    get() = error("Seed slots are write-only; read seeded values via injection inside the scope.")
    set(value) {
        seed(Key.get(UppaalConfig::class.java), value)
    }

var Seed.uppaalExecutorSpec: UppaalExecutorSpec
    get() = error("Seed slots are write-only; read seeded values via injection inside the scope.")
    set(value) {
        seed(Key.get(UppaalExecutorSpec::class.java), value)
    }

suspend fun <T> withUppaalVerificationScope(
    request: VerificationRequest,
    config: UppaalConfig,
    executorSpec: UppaalExecutorSpec,
    block: suspend () -> T,
): T {
    return withVerificationScope(
        Seed().apply {
            this.verificationRequest = request
            this.uppaalConfig = config
            this.uppaalExecutorSpec = executorSpec
        },
        block,
    )
}

internal object UppaalBackendModule : AbstractModule() {
    override fun configure() {
        bindScope(VerificationScoped::class.java, VerificationScope)

        bind(VerificationRequest::class.java)
            .toProvider(Provider { error("VerificationRequest must be seeded into the verification scope") })
            .`in`(VerificationScope)
        bind(UppaalConfig::class.java)
            .toProvider(Provider { error("UppaalConfig must be seeded into the verification scope") })
            .`in`(VerificationScope)
        bind(UppaalExecutorSpec::class.java)
            .toProvider(Provider { error("UppaalExecutorSpec must be seeded into the verification scope") })
            .`in`(VerificationScope)
    }

    @Provides
    @VerificationScoped
    fun provideUppaalArtifactManager(request: VerificationRequest): UppaalArtifactManager {
        return UppaalArtifactManager(request.artifactOutputPath)
    }
}

@VerificationScoped
internal class UppaalVerificationContext @Inject constructor(
    private val config: UppaalConfig,
    private val executorSpec: UppaalExecutorSpec,
    request: VerificationRequest,
    private val artifactManager: UppaalArtifactManager,
    private val uppaalModelGenerator: UppaalModelGenerator,
    private val traceParser: UppaalTraceParser,
    private val witnessTransformer: UppaalInlinedOxstsWitnessTransformer,
) : VerificationContextBase(backendId = "uppaal:${config.id}", request = request) {
    private val logger by loggerFactory()
    private val configId = backendId

    override suspend fun runVerification(
        metadata: VerificationRunMetadata,
        totalMark: TimeSource.Monotonic.ValueTimeMark,
    ): BackendVerificationResult {
        val (artifacts, preparationDuration) = measureTimedValue { prepare() }
        logger.info { "[$configId] OXSTS -> Uppaal model prepared in $preparationDuration" }

        val (verdict, verificationDuration) = measureTimedValue { runVerifyta() }
        logger.info { "[$configId] verifyta returned $verdict in $verificationDuration" }

        val interpreted = interpretVerdict(verdict)
        logger.info { "[$configId] verdict $interpreted for '${request.case.qualifiedName}' (total ${totalMark.elapsedNow()})" }

        var witness: InlinedOxstsAssumptionWitness? = null
        var backAnnotationDuration = Duration.ZERO
        if (verdict != null) {
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
            logger.info { "[$configId] no diagnostic trace in ${artifactManager.logFile.name}; witness not produced" }
            return null
        }
        logger.info { "[$configId] parsed Uppaal trace with ${trace.states.size} state(s); building witness" }
        return witnessTransformer.transform(request.input, trace)
    }

    private fun prepare(): UppaalArtifacts {
        logger.info { "[$configId] generating Uppaal XML and query files" }
        val artifacts = uppaalModelGenerator.generate(request.input)
        artifactManager.modelFile.apply { parentFile?.mkdirs() }.writeText(artifacts.modelXml)
        artifactManager.queryFile.writeText(artifacts.query + "\n")
        return artifacts
    }

    private suspend fun runVerifyta(): UppaalVerdict? {
        val executor = UppaalExecutor.of(executorSpec)
        val workingDir = artifactManager.modelFile.parentFile
        val params = config.parameters.takeIf { it.isNotBlank() }?.split(" ") ?: emptyList()
        val command = params + listOf(artifactManager.modelFile.name, artifactManager.queryFile.name)

        val spec = UppaalExecutionSpecification(
            workingDirectory = workingDir,
            command = command,
            logFile = artifactManager.logFile,
            errorFile = artifactManager.errorFile,
        )

        val result = executor.execute(spec)

        artifactManager.errorFile
            .takeIf { it.exists() }
            ?.useLines { lines ->
                lines.firstOrNull { it.contains("is out of range", ignoreCase = true) }
            }
            ?.let { offending ->
                throw BackendUnsupportedException(
                    "Uppaal cannot represent the OXSTS model: $offending. " +
                        "Uppaal's bounded `int` cannot hold a value the model assigns; " +
                        "no faithful backend encoding is available without a tighter source bound.",
                )
            }

        if (result.exitCode != 0 && result.exitCode != 1) {
            error("verifyta execution failed with exit code ${result.exitCode}")
        }

        return artifactManager.logFile
            .takeIf { it.exists() }
            ?.useLines { lines -> lines.firstNotNullOfOrNull(::detectVerdict) }
    }

    private fun interpretVerdict(verdict: UppaalVerdict?): VerificationVerdict {
        return when (verdict) {
            UppaalVerdict.Satisfied -> VerificationVerdict.Passed
            UppaalVerdict.Unsatisfied -> VerificationVerdict.Failed
            null -> VerificationVerdict.Inconclusive
        }
    }

    private fun detectVerdict(line: String): UppaalVerdict? {
        val trimmed = line.trim()
        return when {
            trimmed.contains("Formula is NOT satisfied", ignoreCase = true) -> UppaalVerdict.Unsatisfied
            trimmed.contains("Property is NOT satisfied", ignoreCase = true) -> UppaalVerdict.Unsatisfied
            trimmed.contains("Formula is satisfied", ignoreCase = true) -> UppaalVerdict.Satisfied
            trimmed.contains("Property is satisfied", ignoreCase = true) -> UppaalVerdict.Satisfied
            else -> null
        }
    }

    private enum class UppaalVerdict { Satisfied, Unsatisfied }
}
