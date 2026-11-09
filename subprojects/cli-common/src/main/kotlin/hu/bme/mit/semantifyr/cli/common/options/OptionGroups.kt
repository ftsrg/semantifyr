/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common.options

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.theta.THETA_DEFAULT_IMAGE
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorKey
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutor
import hu.bme.mit.semantifyr.cli.common.CommandExit
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactKind
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationStepsConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPass
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.discovery.CaseFilter
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.isWritable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VerificationCaseSpecificationOptionGroup(
    private val verificationCaseDiscoverer: VerificationCaseDiscoverer,
) : OptionGroup("Verification case specification") {
    val caseNames by option("--case-name")
        .multiple()
        .help("Qualified name of a case, repeatable.")

    val includeTags by option("--include-tag")
        .multiple()
        .help("Select the cases with this tag, repeatable.")

    val excludeTags by option("--exclude-tag")
        .multiple()
        .help("Skip the cases with this tag, repeatable.")

    fun collectVerificationCases(context: SemantifyrModelContext): List<VerificationCase> {
        if (caseNames.isNotEmpty()) {
            return caseNames.map {
                verificationCaseDiscoverer.findByQualifiedNameOrNull(context, it)
                    ?: throw CommandExit(ExitCode.Input, "No verification case is named '$it'.")
            }
        }
        val cases = verificationCaseDiscoverer.discover(
            context,
            CaseFilter.Tags(including = includeTags.toSet(), excluding = excludeTags.toSet()),
        )
        if (cases.isEmpty()) {
            throw CommandExit(ExitCode.Input, "No verification case matches the selection.")
        }
        return cases
    }
}

enum class ArtifactPreset {
    None,
    All,
    Debug,
}

class ArtifactOptionGroup : OptionGroup("Artifact options") {
    val artifactDirectory by option("-d", "--artifact-directory")
        .path(canBeFile = false, canBeDir = true)
        .help("Directory of the artifacts.")

    val artifactPreset by option("--artifacts")
        .enum<ArtifactPreset>(ignoreCase = true)
        .default(ArtifactPreset.All)
        .help("Artifact preset.")

    val enableArtifacts by option("--enable-artifact")
        .enum<ArtifactKind>(ignoreCase = true)
        .multiple()
        .help("Artifact kind to write, repeatable.")

    val disableArtifacts by option("--disable-artifact")
        .enum<ArtifactKind>(ignoreCase = true)
        .multiple()
        .help("Artifact kind to skip, repeatable.")

    val resolvedOutputDirectory by lazy {
        artifactDirectory ?: Files.createTempDirectory("semantifyr-")
    }

    val resolved by lazy {
        val base = when (artifactPreset) {
            ArtifactPreset.None -> ArtifactConfig.NONE
            ArtifactPreset.All -> ArtifactConfig.ALL
            ArtifactPreset.Debug -> ArtifactConfig.DEBUG
        }
        val resolved = base.enabled + enableArtifacts.toSet() - disableArtifacts.toSet()
        val compilationSteps = if (ArtifactKind.CompilationStep in resolved) {
            CompilationStepsConfig.All
        } else {
            CompilationStepsConfig.Off
        }
        ArtifactConfig(enabled = resolved, enabledCompilationSteps = compilationSteps)
    }

    fun checkOutputDirectory() {
        val directory = resolvedOutputDirectory
        try {
            directory.createDirectories()
        } catch (exception: IOException) {
            throw CommandExit(ExitCode.Output, "Artifact directory '$directory' cannot be written: ${exception.message}")
        }
        if (!directory.isWritable()) {
            throw CommandExit(ExitCode.Output, "Artifact directory '$directory' cannot be written.")
        }
    }
}

enum class ThetaExecutor {
    Auto,
    Shell,
    Docker,
}

class BackendOptionGroup(
    private val portfolios: () -> List<VerificationPortfolio>,
) : OptionGroup("Backend options") {
    val portfolio by option("--portfolio")
        .convert("text") { id ->
            portfolios().firstOrNull { it.id == id } ?: fail("unknown portfolio '$id'")
        }
        .defaultLazy(defaultForHelp = Portfolios.Default.id) {
            portfolios().first {
                it.id == Portfolios.Default.id
            }
        }
        .help("Portfolio id.")

    val thetaExecutor by option("--theta-executor")
        .enum<ThetaExecutor>(ignoreCase = true)
        .default(ThetaExecutor.Auto)
        .help("How to run Theta.")

    val thetaDockerImage by option("--theta-docker-image")
        .default(THETA_DEFAULT_IMAGE)
        .help("Theta Docker image.")

    val timeoutSeconds by option("-t", "--timeout-seconds")
        .long()
        .restrictTo(min = 1L)
        .default(300L)
        .help("Timeout per case, in seconds.")

    val timeout: Duration
        get() = timeoutSeconds.seconds

    val resolved by lazy {
        val builder = ExecutionEnvironment.builder()
        when (thetaExecutor) {
            ThetaExecutor.Auto -> builder.put(ThetaExecutorKey) { ThetaXstsExecutor.autoDetect(thetaDockerImage) }
            ThetaExecutor.Shell -> builder.put(ThetaExecutorKey) { ThetaXstsExecutor.shell() }
            ThetaExecutor.Docker -> builder.put(ThetaExecutorKey) { ThetaXstsExecutor.docker(thetaDockerImage) }
        }
        builder.build()
    }

    fun checkAvailable() {
        val availability = portfolio.availability(resolved)
        if (availability is AvailabilityReport.Unavailable) {
            val hints = availability.hints.joinToString("") {
                "\n  - $it"
            }
            throw CommandExit(ExitCode.Errored, "Portfolio '${portfolio.id}' is unavailable: ${availability.reason}$hints")
        }
    }
}

enum class OptimizationPreset {
    None,
    All,
}

class CompilationOptionGroup : OptionGroup("Compilation options") {
    val optimizationPreset by option("--optimization")
        .enum<OptimizationPreset>(ignoreCase = true)
        .default(OptimizationPreset.All)
        .help("Optimization preset.")

    val enableOptimizations by option("--enable-optimization")
        .enum<OptimizationPass>(ignoreCase = true)
        .multiple()
        .help("Optimization pass to run, repeatable.")

    val disableOptimizations by option("--disable-optimization")
        .enum<OptimizationPass>(ignoreCase = true)
        .multiple()
        .help("Optimization pass to skip, repeatable.")

    val resolved by lazy {
        val base = when (optimizationPreset) {
            OptimizationPreset.None -> OptimizationConfig.NONE.enabled
            OptimizationPreset.All -> OptimizationConfig.ALL.enabled
        }
        val resolved = base + enableOptimizations.toSet() - disableOptimizations.toSet()
        OptimizationConfig(enabled = resolved)
    }
}
