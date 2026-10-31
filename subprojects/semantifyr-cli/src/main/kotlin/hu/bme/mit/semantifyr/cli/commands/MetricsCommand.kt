/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.cli.commands.options.CompilationOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.VerificationCaseSpecificationOptionGroup
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.CompilationMetrics
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.io.path.createTempDirectory

class MetricsCommand @Inject constructor(
    semantifyrLoader: SemantifyrLoader,
    verificationCaseDiscoverer: VerificationCaseDiscoverer,
    private val injector: Injector,
) : BaseSemantifyrCommand("metrics", semantifyrLoader) {
    private val logger by loggerFactory()

    private val caseSpecificationOptions by VerificationCaseSpecificationOptionGroup(verificationCaseDiscoverer)
    private val compilationOptions by CompilationOptionGroup()

    private val json by option("--json")
        .flag()
        .help("Print one JSON object per case, one per line, with the sizes under the keys report.json uses.")

    override fun help(context: Context): String {
        return "Compile one or more verification cases and print their model sizes at input, inlined and flattened level."
    }

    override suspend fun run() {
        logger.info { "metrics model=$model libraries=$libraries" }

        checkInputs()
        val semantifyrModelContext = readModelContext()
        val cases = caseSpecificationOptions.collectVerificationCases(semantifyrModelContext)

        val compiler = SemantifyrCompiler(injector, ArtifactConfig.NONE, compilationOptions.resolved)
        val outputDirectory = createTempDirectory("semantifyr-metrics-")
        try {
            for (case in cases) {
                val metrics = compileCase(compiler, case, outputDirectory).compilationMetrics
                if (json) {
                    echo(jsonLine(case.qualifiedName, metrics))
                } else {
                    echo(case.qualifiedName)
                    echo("  input      ${sizeColumns(metrics.inputSize)}")
                    echo("  inlined    ${sizeColumns(metrics.inlinedSize)}")
                    echo("  flattened  ${sizeColumns(metrics.flattenedSize)}")
                }
            }
        } finally {
            outputDirectory.toFile().deleteRecursively()
        }
    }

    private fun sizeColumns(size: ModelSize): String {
        return "variables ${size.variables}  operations ${size.operations}  expressions ${size.expressions}"
    }

    private fun jsonLine(qualifiedName: String, metrics: CompilationMetrics): String {
        return buildJsonObject {
            put("case", qualifiedName)
            put("inputSize", Json.encodeToJsonElement(metrics.inputSize))
            put("inlinedSize", Json.encodeToJsonElement(metrics.inlinedSize))
            put("flattenedSize", Json.encodeToJsonElement(metrics.flattenedSize))
        }.toString()
    }
}
