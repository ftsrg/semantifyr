/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.internal.VerificationArtifactWriter
import hu.bme.mit.semantifyr.verifier.witness.ClassWitnessBackAnnotator
import kotlinx.serialization.json.Json
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.moveTo
import kotlin.io.path.readText

@OptIn(ExperimentalPathApi::class)
fun main(arguments: Array<String>) {
    val (modelPath, reportPath, witnessPath) = arguments.map {
        Path(it)
    }
    val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    val report = Json.decodeFromString<VerificationReport>(reportPath.readText())
    val trace = requireNotNull(report.trace) {
        "the report $reportPath holds no trace"
    }
    val context = injector.getInstance(SemantifyrLoader::class.java).loadStandaloneModel(modelPath)
    val case = injector.getInstance(VerificationCaseDiscoverer::class.java).findByQualifiedName(context, report.verificationCase)
    val caseDirectory = createTempDirectory("witness")
    try {
        val compilation = SemantifyrCompiler(injector, ArtifactConfig.NONE, report.optimization).compile(case.classDeclaration, caseDirectory)
        val witness = injector.getInstance(ClassWitnessBackAnnotator::class.java).createWitnessInlinedOxsts(trace, compilation, report.result.verdict)
        injector.getInstance(VerificationArtifactWriter::class.java).writeWitness(witness, caseDirectory, case.qualifiedName).moveTo(witnessPath, overwrite = true)
    } finally {
        caseDirectory.deleteRecursively()
    }
}
