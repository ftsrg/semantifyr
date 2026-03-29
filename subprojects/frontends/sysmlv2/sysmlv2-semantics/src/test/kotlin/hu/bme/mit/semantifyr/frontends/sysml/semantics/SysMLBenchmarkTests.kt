/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.semantics

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.backends.theta.verification.ThetaVerifier
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.semantics.BaseSemantifyrVerificationTest
import hu.bme.mit.semantifyr.semantics.InjectWithOxstsSemantics
import hu.bme.mit.semantifyr.semantics.SemantifyrVerificationHelper
import hu.bme.mit.semantifyr.semantics.StandaloneOxstsSemanticsRuntimeModule
import hu.bme.mit.semantifyr.semantics.utils.info
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.toDuration

@Tag("benchmark")
@InjectWithOxstsSemantics
class SysMLBenchmarkTests : BaseSemantifyrVerificationTest<ThetaVerifier>() {

    @Serializable
    private data class BenchmarkRow(
        val model: String,
        val case: String,
        val run: Int,
        val inliningS: Double,
        val xstsTransformS: Double,
        val verificationS: Double,
        val backAnnotationS: Double,
        val totalS: Double,
        val result: String,
    )

    companion object {

        private val semantifyrVerificationHelper =
            StandaloneOxstsSemanticsRuntimeModule.getInstance<SemantifyrVerificationHelper>()

        private val transformer = StandaloneSysMLTransformer()

        private val BENCHMARK_MODELS = listOf(
            "TestModels/crossroads.sysml" to "crossroads",
            "TestModels/compressedspacecraft.sysml" to "compressedspacecraft",
            "TestModels/spacecraft.sysml" to "spacecraft",
        )

        private fun loadModel(model: String) = run {
            val sysmlModelPath = Path(model)
            val sysmlModel = sysmlModelPath.toFile()
            val oxstsModelPath = Path(sysmlModel.absolutePath.replace(".sysml", ".oxsts"))
            transformer.transformModel(sysmlModel, oxstsModelPath.toFile())
            semantifyrVerificationHelper.semantifyrLoader.startContext()
                .loadLibraries(Path("Library"))
                .loadModel(oxstsModelPath)
                .buildAndResolve()
        }

    }

    override val logger by loggerFactory()

    @Inject
    override lateinit var oxstsVerifierProvider: Provider<ThetaVerifier>

    @Test
    fun `Benchmark Verification Cases`() {
        val runs = System.getProperty("benchmark.runs", "5").toInt()
        val rows = mutableListOf<BenchmarkRow>()

        for ((modelPath, modelName) in BENCHMARK_MODELS) {
            logger.info { "Loading model for benchmark: $modelPath" }
            val model = loadModel(modelPath)
            val cases = semantifyrVerificationHelper.collectVerificationCases(model)

            for (case in cases) {
                @Suppress("UNCHECKED_CAST")
                val named = case.get()[0] as Named<ClassDeclaration>
                val caseName = named.name
                val classDeclaration = named.payload

                logger.info { "Benchmarking $modelName / $caseName  ($runs run(s))" }

                repeat(runs) { runIndex ->
                    try {
                        logger.info { "Running $runIndex of $modelName / $caseName" }

                        compilationScopeHelper.runInCompilationScope(classDeclaration) { scopedDecl ->
                            val result = executeVerificationCase(scopedDecl, 10.toDuration(DurationUnit.MINUTES))
                            val m = result.metrics
                            rows += BenchmarkRow(
                                model = modelName,
                                case = caseName,
                                run = runIndex,
                                inliningS = m.inliningDuration.toDouble(DurationUnit.SECONDS),
                                xstsTransformS = m.xstsTransformationDuration.toDouble(DurationUnit.SECONDS),
                                verificationS = m.verificationDuration.toDouble(DurationUnit.SECONDS),
                                backAnnotationS = m.backAnnotationDuration.toDouble(DurationUnit.SECONDS),
                                totalS = m.totalDuration.toDouble(DurationUnit.SECONDS),
                                result = result.result.name,
                            )
                        }
                    } catch (e: Exception) {
                        logger.error("Run $runIndex of $modelName / $caseName failed, skipping", e)
                    }
                }
            }
        }

        writeResults(rows)
        printSummary(rows, runs)
    }

    // -------------------------------------------------------------------------

    private fun writeResults(rows: List<BenchmarkRow>) {
        val outputFile = File("TestModels/benchmark-results.json")
        outputFile.parentFile?.mkdirs()
        val prettyJson = Json { prettyPrint = true }
        outputFile.writeText(prettyJson.encodeToString(rows))
        logger.info { "Benchmark results written to ${outputFile.absolutePath}" }
    }

    private fun printSummary(rows: List<BenchmarkRow>, runs: Int) {
        println("\n=== Benchmark Summary ($runs run(s) per case) ===")
        println("%-60s  %8s  %8s  %8s  %8s  %8s".format(
            "model/case", "inl.mean", "inl.max", "ver.mean", "ver.max", "tot.mean"))
        println("-".repeat(110))

        val grouped = rows.groupBy { "${it.model}/${it.case}" }
        for ((key, group) in grouped.entries.sortedBy { it.key }) {
            val inl = group.map { it.inliningS }
            val ver = group.map { it.verificationS }
            val tot = group.map { it.totalS }
            println("%-60s  %8.3f  %8.3f  %8.3f  %8.3f  %8.3f".format(
                key,
                inl.average(), inl.max(),
                ver.average(), ver.max(),
                tot.average()))
        }
    }

}
