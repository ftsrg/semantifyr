/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.verification

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.Runexec
import hu.bme.mit.semantifyr.benchmark.Cell
import hu.bme.mit.semantifyr.benchmark.CellAction
import hu.bme.mit.semantifyr.benchmark.CpuClock
import hu.bme.mit.semantifyr.benchmark.Measurement
import hu.bme.mit.semantifyr.benchmark.Outcome
import hu.bme.mit.semantifyr.benchmark.RunexecTotals
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationReport
import hu.bme.mit.semantifyr.verifier.qualifiedNameToDirectoryName
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import java.nio.file.Path
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.TimeSource

class VerificationCellAction(
    private val injector: Injector,
    private val timeout: Duration,
    private val environment: ExecutionEnvironment,
    private val modelDirectory: Path,
    private val runexecOptions: List<String>? = null,
) : CellAction {
    private val logger by loggerFactory()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val availability = mutableMapOf<String, AvailabilityReport>()

    private var preparedModelName: String? = null
    private var preparedModel: PreparedModel? = null

    override fun unavailability(cell: Cell): String? {
        val portfolio = cell[VerificationAxes.backendConfig]
        val report = availability.getOrPut(portfolio.id) {
            portfolio.availability(environment)
        }
        return (report as? AvailabilityReport.Unavailable)?.reason
    }

    override suspend fun run(cell: Cell, workDirectory: Path): Measurement {
        val case = cell[VerificationAxes.case]
        val prepared = prepare(cell[VerificationAxes.model])
        val verificationCase = prepared.cases[case.name] ?: error("Model ${cell[VerificationAxes.model].name} has no case ${case.name}")
        val resourceSet = verificationCase.classDeclaration.eResource().resourceSet
        val resourcesBefore = resourceSet.resources.toSet()
        try {
            val verifier = SemantifyrVerifier.builder()
                .injector(injector)
                .portfolio(cell[VerificationAxes.backendConfig])
                .environment(environment)
                .artifacts(ArtifactConfig.NONE)
                .outputDirectory(workDirectory)
                .timeout(timeout)
                .optimization(cell[VerificationAxes.optimization].config)
                .build()

            // runexec enforces the CPU-time limit, the verifier still enforces the wall-time one
            val runexec = runexecOptions?.let {
                Runexec(it + listOf("--timelimit", timeout.inWholeSeconds.toString()))
            }
            val cpuBefore = CpuClock.sample()
            val mark = TimeSource.Monotonic.markNow()
            withContext(runexec ?: EmptyCoroutineContext) {
                verifier.verify(verificationCase)
            }
            val wallTime = mark.elapsedNow()
            val cpu = CpuClock.sample() - cpuBefore

            val reportFile = workDirectory.resolve(qualifiedNameToDirectoryName(verificationCase.qualifiedName)).resolve("report.json")
            val result = json.decodeFromString<VerificationReport>(reportFile.readText()).result
            return Measurement(
                outcome = Outcome.of(result.verdict),
                verdict = result.verdict,
                expected = case.expected,
                falsification = case.falsification,
                decidedBy = result.metadata.backendId.takeIf { result.isDecisive },
                wallTime = wallTime,
                cpuTime = cpu.total,
                backendCpuTime = cpu.children,
                runexec = runexec?.runs?.takeIf { it.isNotEmpty() }?.let {
                    RunexecTotals.of(it)
                },
                message = result.message,
                metrics = result.metrics,
                extensions = prepared.extensions,
            )
        } finally {
            unloadCompiledModels(resourceSet, resourcesBefore)
        }
    }

    private fun prepare(model: BenchmarkModel): PreparedModel {
        val cached = preparedModel
        if (cached != null && preparedModelName == model.name) {
            return cached
        }
        preparedModel = null
        val directory = modelDirectory.resolve(UUID.randomUUID().toString()).createDirectories()
        try {
            logger.info { "Preparing model ${model.name}" }
            val prepared = model.prepare(directory)
            preparedModelName = model.name
            preparedModel = prepared
            return prepared
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    // the inlined model and the back-annotated witness of every case land in the shared resource set
    private fun unloadCompiledModels(resourceSet: ResourceSet, resourcesBefore: Set<Resource>) {
        resourceSet.resources.filter {
            it !in resourcesBefore && it.contents.any { root -> root is InlinedOxsts }
        }.forEach {
            it.unload()
            resourceSet.resources.remove(it)
        }
    }
}
