/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.verification

import com.google.inject.AbstractModule
import com.google.inject.Injector
import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import com.google.inject.assistedinject.FactoryModuleBuilder
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationContext
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.scopes.VerificationScope
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backends.spin.SpinExecutionSpecification
import hu.bme.mit.semantifyr.backends.spin.SpinExecutor
import hu.bme.mit.semantifyr.backends.spin.SpinExecutorKey
import hu.bme.mit.semantifyr.backends.spin.SpinReplaySpecification
import hu.bme.mit.semantifyr.backends.spin.artifacts.SpinArtifactManager
import hu.bme.mit.semantifyr.backends.spin.trace.SpinInlinedOxstsWitnessTransformer
import hu.bme.mit.semantifyr.backends.spin.trace.SpinTraceParser
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinArtifacts
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinModelTransformer
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn

class SpinBackend : VerificationBackend<SpinConfig>() {
    override val id: String = "spin"
    override val executorKey = SpinExecutorKey

    private val logger by loggerFactory()

    override suspend fun verify(
        parentInjector: Injector,
        config: SpinConfig,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        val executor = environment[SpinExecutorKey]
        logger.info { "[spin:${config.id}] dispatching via ${executor::class.simpleName}" }

        val injector = parentInjector.createChildInjector(SpinBackendModule())
        return withVerificationScope {
            val factory = injector.getInstance(SpinVerificationContext.Factory::class.java)
            val context = factory.create(config, executor, request)
            context.execute()
        }
    }
}

internal class SpinBackendModule : AbstractModule() {
    override fun configure() {
        bindScope(VerificationScoped::class.java, VerificationScope)
        install(FactoryModuleBuilder().build(SpinVerificationContext.Factory::class.java))

        super.configure()
    }
}

class SpinVerificationContext @AssistedInject constructor(
    @param:Assisted private val config: SpinConfig,
    @param:Assisted override val executor: SpinExecutor,
    @Assisted request: BackendVerificationRequest,
    private val spinModelTransformer: SpinModelTransformer,
    private val traceParser: SpinTraceParser,
    private val witnessTransformer: SpinInlinedOxstsWitnessTransformer,
) : VerificationContext<SpinArtifacts, SpinVerificationContext.SpinVerdict>(
    backendId = "spin:${config.id}",
    request = request,
) {

    private val artifactManager = SpinArtifactManager(request.artifactOutputPath)

    override suspend fun prepare(): SpinArtifacts {
        logger.info { "[$backendId] generating Promela model" }
        val artifacts = spinModelTransformer.transform(request.inlinedOxsts)
        artifactManager.modelFile.apply {
            parentFile?.mkdirs()
        }.writeText(artifacts.promela)
        return artifacts
    }

    override suspend fun run(artifacts: SpinArtifacts): SpinVerdict? {
        val spec = SpinExecutionSpecification(
            workingDirectory = artifactManager.modelFile.parentFile,
            modelFileName = artifactManager.modelFile.name,
            extraArguments = config.extraArguments,
            logFile = artifactManager.logFile,
            errorFile = artifactManager.errorFile,
        )

        val result = executor.execute(spec)

        // Spin returns 1 on counterexample; treat non-crash exits as valid and rely on output parsing.
        if (result.exitCode != 0 && result.exitCode != 1) {
            error("spin execution failed with exit code ${result.exitCode}")
        }

        return artifactManager.logFile
            .takeIf { it.exists() }
            ?.useLines { lines -> detectVerdict(lines) }
    }

    override fun interpret(rawVerdict: SpinVerdict?, artifacts: SpinArtifacts): VerificationVerdict {
        val verdict = if (artifacts.property.invertVerdict) {
            rawVerdict?.invert()
        } else {
            rawVerdict
        }
        return when (verdict) {
            SpinVerdict.True -> VerificationVerdict.Passed
            SpinVerdict.False -> VerificationVerdict.Failed
            null -> VerificationVerdict.Inconclusive
        }
    }

    override suspend fun buildWitness(rawVerdict: SpinVerdict?, artifacts: SpinArtifacts): InlinedWitness? {
        if (rawVerdict != SpinVerdict.False || !artifactManager.trailFile.exists()) {
            return null
        }
        val replaySpec = SpinReplaySpecification(
            workingDirectory = artifactManager.modelFile.parentFile,
            modelFileName = artifactManager.modelFile.name,
            logFile = artifactManager.replayLogFile,
            errorFile = artifactManager.errorFile,
        )
        val replayResult = executor.replayTrail(replaySpec)
        if (replayResult.exitCode != 0 && replayResult.exitCode != 1) {
            logger.warn { "[$backendId] spin replay failed with exit code ${replayResult.exitCode}; witness not produced" }
            return null
        }
        val trace = traceParser.parse(artifactManager.replayLogFile)
        if (trace == null) {
            logger.warn { "[$backendId] no parseable steps in spin replay; witness not produced" }
            return null
        }
        logger.info { "[$backendId] parsed Spin trace with ${trace.states.size} state(s); building witness" }
        return witnessTransformer.transform(request.inlinedOxsts, trace)
    }

    private fun detectVerdict(lines: Sequence<String>): SpinVerdict? {
        var explicitFailure = false
        var explicitSuccess = false
        for (raw in lines) {
            val line = raw.trim()
            val lower = line.lowercase()
            // Counterexample signals - either LTL claim violated or liveness acceptance cycle found.
            if ("violated" in lower || "acceptance cycle" in lower || "assertion violated" in lower) {
                explicitFailure = true
            }
            // Spin's summary line after a clean run. Reliable when combined with absence of failure signals.
            if (lower.contains("errors: 0")) {
                explicitSuccess = true
            }
        }
        return when {
            explicitFailure -> SpinVerdict.False
            explicitSuccess -> SpinVerdict.True
            else -> null
        }
    }

    enum class SpinVerdict {
        True,
        False,
        ;

        fun invert(): SpinVerdict {
            return if (this == True) {
                False
            } else {
                True
            }
        }
    }

    interface Factory {
        fun create(
            config: SpinConfig,
            executor: SpinExecutor,
            request: BackendVerificationRequest,
        ): SpinVerificationContext
    }
}
