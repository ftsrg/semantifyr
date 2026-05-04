/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.internal

import com.google.inject.Inject
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.verification.Trace
import hu.bme.mit.semantifyr.verification.VerificationReport
import kotlinx.serialization.json.Json
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class VerificationArtifactWriter @Inject constructor(
    private val serializer: ISerializer,
) {

    private val logger by loggerFactory()

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun writeReport(caseArtifactPath: Path, report: VerificationReport) {
        val reportPath = caseArtifactPath.resolve("report.json")
        caseArtifactPath.createDirectories()
        reportPath.writeText(json.encodeToString(report))
    }

    fun writeWitnessArtifacts(
        trace: Trace,
        caseArtifactPath: Path,
        qualifiedName: String,
    ) {
        writeWitness(trace, caseArtifactPath, qualifiedName)
        writeWitnessState(trace, caseArtifactPath, qualifiedName)
        writeCallTrace(trace, caseArtifactPath, qualifiedName)
    }

    private fun writeWitness(
        trace: Trace,
        caseArtifactPath: Path,
        qualifiedName: String,
    ) {
        val witnessPath = caseArtifactPath.resolve("witness.oxsts")
        try {
            witnessPath.parent?.createDirectories()
            val witness = trace.backAnnotatedModel
            val resourceSet = witness.classDeclaration.eResource().resourceSet
            val witnessUri = URI.createFileURI(witnessPath.toAbsolutePath().toString())
            resourceSet.getResource(witnessUri, false)?.delete(emptyMap<Any, Any>())
            resourceSet.createResource(witnessUri).contents += witness
            witnessPath.bufferedWriter().use {
                serializer.serialize(witness, it, SaveOptions.defaultOptions())
            }
            logger.info { "[$qualifiedName] wrote witness artifact to $witnessPath" }
        } catch (e: Exception) {
            logger.warn("[$qualifiedName] failed to write witness artifact at $witnessPath: ${e.message ?: e::class.simpleName}", e)
        }
    }

    private fun writeWitnessState(
        trace: Trace,
        caseArtifactPath: Path,
        qualifiedName: String,
    ) {
        val statePath = caseArtifactPath.resolve("witness.json")
        try {
            statePath.parent?.createDirectories()
            statePath.writeText(json.encodeToString(trace.witnessState))
            logger.info { "[$qualifiedName] wrote witness state artifact to $statePath" }
        } catch (e: Exception) {
            logger.warn("[$qualifiedName] failed to write witness state artifact at $statePath: ${e.message ?: e::class.simpleName}", e)
        }
    }

    private fun writeCallTrace(
        trace: Trace,
        caseArtifactPath: Path,
        qualifiedName: String,
    ) {
        val tracePath = caseArtifactPath.resolve("trace.json")
        try {
            tracePath.parent?.createDirectories()
            tracePath.writeText(json.encodeToString(trace.callTrace))
            logger.info { "[$qualifiedName] wrote call trace artifact to $tracePath" }
        } catch (e: Exception) {
            logger.warn("[$qualifiedName] failed to write trace artifact at $tracePath: ${e.message ?: e::class.simpleName}", e)
        }
    }

}
