/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.sources

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.benchmark.RecordExtension
import hu.bme.mit.semantifyr.benchmark.verification.BenchmarkCase
import hu.bme.mit.semantifyr.benchmark.verification.BenchmarkModel
import hu.bme.mit.semantifyr.benchmark.verification.PreparedModel
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Frontend
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusModel
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusVerdict
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLCorpus
import hu.bme.mit.semantifyr.frontends.sysml.wrapper.SysMLv2Compiler
import hu.bme.mit.semantifyr.portfolios.Portfolios
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.measureTimedValue

@Serializable
data class SysMLMeasurement(
    val transformationDuration: Duration,
    val lines: Int,
    val parts: Int,
    val states: Int,
    val transitions: Int,
    val sends: Int,
    val verificationCases: Int,
) {
    companion object {
        val extension = RecordExtension("sysml", serializer())

        private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        private val lineComment = Regex("""//[^\n]*""")

        fun count(source: String, transformationDuration: Duration): SysMLMeasurement {
            val code = source.replace(blockComment, "").replace(lineComment, "")
            return SysMLMeasurement(
                transformationDuration = transformationDuration,
                lines = code.lines().count {
                    it.isNotBlank()
                },
                parts = Regex("""\bpart\b""").findAll(code).count(),
                states = Regex("""\bstate\b""").findAll(code).count(),
                transitions = Regex("""\btransition\b""").findAll(code).count(),
                sends = Regex("""\bsend\b""").findAll(code).count(),
                verificationCases = Regex("""\bverification\s+def\b""").findAll(code).count(),
            )
        }
    }
}

class SysMLCorpusSource(
    private val injector: Injector,
    private val examplesDirectory: Path,
) {
    val corpus: SysMLCorpus by lazy {
        SysMLCorpus.load(examplesDirectory.resolve("manifest.json"))
    }

    val models: List<BenchmarkModel> by lazy {
        corpus.models.map {
            SysMLModel(it)
        }
    }

    fun model(file: String): BenchmarkModel {
        return models.single {
            it.name == file
        }
    }

    private inner class SysMLModel(
        private val model: CorpusModel,
    ) : BenchmarkModel {
        override val name: String = model.file

        override val cases: List<BenchmarkCase> = model.cases.map {
            BenchmarkCase(
                name = it.name,
                expected = when (it.verdict) {
                    CorpusVerdict.Passed -> VerificationVerdict.Passed
                    CorpusVerdict.Failed -> VerificationVerdict.Failed
                    CorpusVerdict.Unknown -> null
                },
                falsification = it.falsification,
                nightly = it.tier == CorpusTier.Nightly,
            )
        }

        override fun prepare(workDirectory: Path): PreparedModel {
            val source = examplesDirectory.resolve(model.file)
            // a fresh cache, so the measured time is the frontend's and not a cache hit
            val compiler = SysMLv2Compiler(workDirectory.resolve("sysml-cache"))
            val (compiled, transformationDuration) = measureTimedValue {
                compiler.compile(source, workDirectory.resolve("${source.nameWithoutExtension}.oxsts"))
            }
            // the frontend only loads the compiled model here, every cell builds its own verifier
            val frontend = SysMLv2Frontend.builder()
                .source(source)
                .variant(model.variant)
                .injector(injector)
                .portfolio(Portfolios.ThetaFull)
                .artifacts(ArtifactConfig.NONE)
                .outputDirectory(workDirectory)
                .compiledOxstsPath(compiled)
                .build()
            val verificationCases = frontend.verificationCases().associateBy {
                it.qualifiedName.substringAfterLast("::")
            }
            return PreparedModel(
                cases = verificationCases,
                extensions = mapOf(SysMLMeasurement.extension.entry(SysMLMeasurement.count(source.readText(), transformationDuration))),
            )
        }
    }
}
