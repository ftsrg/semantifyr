/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaVerificationSpecification
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.serializer.ArtifactManager
import hu.bme.mit.semantifyr.semantics.verification.VerificationCaseRunResult
import hu.bme.mit.semantifyr.semantics.verification.VerificationTimeMetrics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.eclipse.emf.common.util.URI
import java.io.File
import kotlin.time.Duration
import kotlin.time.Instant

@Serializable
data class VerificationReport(
    var className: String,
    var modelPath: String,
    var startedAt: Instant,
    var totalDuration: Duration,
    var result: VerificationCaseRunResult,
    var timeout: Duration,
)

@Serializable
data class VerificationPortfolioResult(
    val winningSpecification: ThetaVerificationSpecification,
    val allArguments: List<String>,
)

@CompilationScoped
class ThetaArtifactManager {

    @Inject
    private lateinit var artifactManager: ArtifactManager

    val xstsFile: File
        get() = artifactManager.resolve("inlined.xsts")

    val xstsUri: URI
        get() = URI.createFileURI(xstsFile.absolutePath)

    fun serialize(data: VerificationTimeMetrics) {
        val file = artifactManager.resolve("metrics${File.separator}verification-metrics.json")
        file.parentFile.mkdirs()

        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        file.outputStream().buffered().use {
            json.encodeToStream(data, it)
        }
    }

    fun serialize(data: VerificationReport) {
        val file = artifactManager.resolve("report.json")
        file.parentFile.mkdirs()

        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            explicitNulls = false
        }

        file.outputStream().buffered().use {
            json.encodeToStream(data, it)
        }
    }

    fun serialize(data: VerificationPortfolioResult) {
        val file = artifactManager.resolve("metrics${File.separator}portfolio.json")
        file.parentFile.mkdirs()

        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        file.outputStream().buffered().use {
            json.encodeToStream(data, it)
        }
    }

}
