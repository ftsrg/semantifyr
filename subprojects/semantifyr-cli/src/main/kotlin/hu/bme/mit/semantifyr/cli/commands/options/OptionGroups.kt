/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands.options

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorSpec
import hu.bme.mit.semantifyr.backends.theta.verification.theta
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.semantics.StandaloneOxstsSemanticsRuntimeModule
import hu.bme.mit.semantifyr.semantics.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.semantics.artifact.ArtifactKind
import hu.bme.mit.semantifyr.semantics.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.semantics.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.semantics.verification.ExecutionEnvironment
import hu.bme.mit.semantifyr.semantics.verification.VerificationCase
import hu.bme.mit.semantifyr.semantics.verification.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.semantics.verification.portfolio.Portfolio
import java.nio.file.Files
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val verificationCaseDiscoverer by lazy {
    StandaloneOxstsSemanticsRuntimeModule.getInstance<VerificationCaseDiscoverer>()
}

class VerificationCaseSpecificationOptionGroup : OptionGroup("Verification case specification") {
    val caseNames by option("--case-name")
        .multiple()
        .help("The fully qualified name(s) of the verification case to be verified. Repeat the flag to add multiple. Overrides tag filters when set.")

    val includeTags by option("--include-tag")
        .multiple()
        .help("Specifies tag inclusion filters.")

    val excludeTags by option("--exclude-tag")
        .multiple()
        .help("Specifies tag exclusion filters.")

    fun collectVerificationCases(context: SemantifyrModelContext): List<VerificationCase> {
        if (caseNames.isNotEmpty()) {
            return caseNames.map {
                verificationCaseDiscoverer.findByName(context, it)
            }
        }
        return verificationCaseDiscoverer.discover(context, including = includeTags.toSet(), excluding = excludeTags.toSet())
    }
}

enum class ArtifactPreset { None, Report, All }

class ArtifactOptionGroup : OptionGroup("Artifact options") {
    val artifactDirectory by option("-d", "--artifact-directory")
        .path(canBeFile = false, canBeDir = true)
        .help("Write artifacts under this directory. Defaults to a temporary directory.")

    val artifactPreset by option("--artifacts")
        .enum<ArtifactPreset>(ignoreCase = true)
        .default(ArtifactPreset.All)
        .help("Artifact preset. Default: 'all'.")

    val enableArtifacts by option("--enable-artifact")
        .enum<ArtifactKind>(ignoreCase = true)
        .multiple()
        .help("Enable a specific artifact kind (can be repeated).")

    val disableArtifacts by option("--disable-artifact")
        .enum<ArtifactKind>(ignoreCase = true)
        .multiple()
        .help("Disable a specific artifact kind (can be repeated).")

    val resolved by lazy {
        val outputDirectory = artifactDirectory ?: Files.createTempDirectory("semantifyr-")
        val base = when (artifactPreset) {
            ArtifactPreset.None -> ArtifactConfig.none(outputDirectory)
            ArtifactPreset.Report -> ArtifactConfig.reportOnly(outputDirectory)
            ArtifactPreset.All -> ArtifactConfig.all(outputDirectory)
        }
        val resolved = base.enabled + enableArtifacts.toSet() - disableArtifacts.toSet()
        base.copy(enabled = resolved)
    }
}

enum class ThetaExecutor { Auto, Shell, Docker }

const val DEFAULT_PORTFOLIO_ID = "theta-full"

class BackendOptionGroup : OptionGroup("Backend options") {
    val portfolioId by option("--portfolio")
        .default(DEFAULT_PORTFOLIO_ID)
        .help("Portfolio id. Default: '$DEFAULT_PORTFOLIO_ID'. Options: ${Portfolios.all.joinToString(", ") { it.id }}")

    val thetaExecutor by option("--theta-executor")
        .enum<ThetaExecutor>(ignoreCase = true)
        .default(ThetaExecutor.Auto)
        .help("Which Theta executor to use. Default: 'auto'. Options: auto, shell, docker")

    val thetaDockerImage by option("--theta-docker-image")
        .default(ThetaExecutorSpec.Docker.DEFAULT_IMAGE)
        .help("Theta docker image reference (repository:tag or @digest), used when --theta-executor=docker. Default: '${ThetaExecutorSpec.Docker.DEFAULT_IMAGE}'.")

    val timeoutSeconds by option("-t", "--timeout-seconds")
        .long()
        .default(300L)
        .help("Total verification wall-clock timeout, in seconds. Default: 300 (5 minutes).")

    val timeout: Duration
        get() = timeoutSeconds.seconds

    fun resolvePortfolio(): Portfolio {
        return Portfolios.byId(portfolioId) ?: error("Unknown portfolio '$portfolioId'. Options: ${Portfolios.all.joinToString { it.id }}")
    }

    val resolved by lazy {
        val spec = when (thetaExecutor) {
            ThetaExecutor.Auto -> ThetaExecutorSpec.Auto
            ThetaExecutor.Shell -> ThetaExecutorSpec.Shell
            ThetaExecutor.Docker -> ThetaExecutorSpec.Docker(image = thetaDockerImage)
        }
        ExecutionEnvironment.builder().theta(spec).build()
    }
}

enum class OptimizationPreset { None, All }

class CompilationOptionGroup : OptionGroup("Compilation options") {
    val optimizationPreset by option("--optimization")
        .enum<OptimizationPreset>(ignoreCase = true)
        .default(OptimizationPreset.All)
        .help("Optimization preset. Default: 'all'.")

    val enableOptimizations by option("--enable-optimization")
        .enum<OptimizationCategory>(ignoreCase = true)
        .multiple()
        .help("Enable a specific optimization category (can be repeated).")

    val disableOptimizations by option("--disable-optimization")
        .enum<OptimizationCategory>(ignoreCase = true)
        .multiple()
        .help("Disable a specific optimization category (can be repeated).")

    val resolved by lazy {
        val base = when (optimizationPreset) {
            OptimizationPreset.None -> OptimizationConfig.NONE.enabled
            OptimizationPreset.All -> OptimizationConfig.ALL.enabled
        }
        val resolved = base + enableOptimizations.toSet() - disableOptimizations.toSet()
        OptimizationConfig(enabled = resolved)
    }
}
