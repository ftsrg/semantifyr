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
import hu.bme.mit.semantifyr.backends.spin.trace.SpinLogVerdictParser
import hu.bme.mit.semantifyr.backends.spin.trace.SpinTraceParser
import hu.bme.mit.semantifyr.backends.spin.trace.SpinVerdict
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinArtifacts
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinModelTransformer
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinOperationVisitor
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.warn

class SpinBackend : VerificationBackend<SpinConfig>() {
    override val id = "spin"
    override val executorKey = SpinExecutorKey

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

class SpinBackendModule : AbstractModule() {
    override fun configure() {
        bindScope(VerificationScoped::class.java, VerificationScope)
        install(FactoryModuleBuilder().build(SpinVerificationContext.Factory::class.java))
        install(FactoryModuleBuilder().build(SpinOperationVisitor.Factory::class.java))

        super.configure()
    }
}

class SpinVerificationContext @AssistedInject constructor(
    @param:Assisted private val config: SpinConfig,
    @param:Assisted override val executor: SpinExecutor,
    @Assisted request: BackendVerificationRequest,
    private val spinModelTransformer: SpinModelTransformer,
    private val spinTraceParser: SpinTraceParser,
    private val spinLogVerdictParser: SpinLogVerdictParser,
    private val spinInlinedOxstsWitnessTransformer: SpinInlinedOxstsWitnessTransformer,
) : VerificationContext<SpinArtifacts, SpinVerdict>(
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

        return artifactManager.logFile.takeIf {
            it.exists()
        }?.useLines {
            spinLogVerdictParser.parse(it)
        }
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
        val trace = spinTraceParser.parse(artifactManager.replayLogFile)
        if (trace == null) {
            logger.warn { "[$backendId] no parseable steps in spin replay; witness not produced" }
            return null
        }
        logger.info { "[$backendId] parsed Spin trace with ${trace.states.size} state(s); building witness" }
        return spinInlinedOxstsWitnessTransformer.transform(request.inlinedOxsts, trace)
    }

    interface Factory {
        fun create(
            config: SpinConfig,
            executor: SpinExecutor,
            request: BackendVerificationRequest,
        ): SpinVerificationContext
    }
}
