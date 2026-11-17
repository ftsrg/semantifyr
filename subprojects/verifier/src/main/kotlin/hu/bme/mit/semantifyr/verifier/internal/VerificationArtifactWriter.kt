/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.internal

import com.google.inject.Inject
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verifier.VerificationReport
import kotlinx.serialization.json.Json
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.resource.FileExtensionProvider
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class VerificationArtifactWriter @Inject constructor(
    private val serializer: ISerializer,
    private val fileExtensionProvider: FileExtensionProvider,
) {

    private val logger by loggerFactory()

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun writeReport(caseDirectory: Path, report: VerificationReport): Path {
        val reportPath = caseDirectory.resolve("report.json")
        caseDirectory.createDirectories()
        reportPath.writeText(json.encodeToString(report))
        return reportPath
    }

    fun writeWitness(
        witness: InlinedOxsts,
        caseDirectory: Path,
        qualifiedName: String,
    ): Path {
        val witnessPath = caseDirectory.resolve("witness.${fileExtensionProvider.primaryFileExtension}")
        witnessPath.parent?.createDirectories()
        val resourceSet = witness.classDeclaration.eResource().resourceSet
        val witnessUri = URI.createFileURI(witnessPath.toAbsolutePath().toString())
        resourceSet.getResource(witnessUri, false)?.delete(emptyMap<Any, Any>())
        resourceSet.createResource(witnessUri).contents += witness
        witnessPath.bufferedWriter().use {
            serializer.serialize(witness, it, SaveOptions.defaultOptions())
        }
        logger.info { "[$qualifiedName] wrote witness artifact to $witnessPath" }
        return witnessPath
    }

}
