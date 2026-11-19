/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import com.google.inject.AbstractModule
import com.google.inject.Injector
import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import com.google.inject.assistedinject.FactoryModuleBuilder
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationContext
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.scopes.VerificationScope
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backends.theta.artifacts.ThetaArtifactManager
import hu.bme.mit.semantifyr.backends.theta.backannotation.CexReader
import hu.bme.mit.semantifyr.backends.theta.backannotation.CexWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.InlinedWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.XstsWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.trace.ThetaLogVerdictParser
import hu.bme.mit.semantifyr.backends.theta.trace.ThetaVerdict
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.ThetaArtifacts
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.ThetaCapabilityChecker
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.ThetaHornCapabilityChecker
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.ThetaModelTransformer
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.XstsModelCreator
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.file.Path

class ThetaBackend : VerificationBackend<ThetaConfig>() {

    override val id = "theta"
    override val executorKey = ThetaExecutorKey

    override suspend fun verify(
        parentInjector: Injector,
        config: ThetaConfig,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        val executor = environment[ThetaExecutorKey]
        logger.info { "[theta:${config.id}] dispatching via ${executor::class.simpleName}" }
        val injector = parentInjector.createChildInjector(ThetaBackendModule())
        return withVerificationScope {
            val factory = injector.getInstance(ThetaVerificationContext.Factory::class.java)
            val context = factory.create(config, executor, request, capabilityChecker(parentInjector, config))
            context.execute()
        }
    }

    override fun capabilityChecker(parentInjector: Injector, config: ThetaConfig): BackendCapabilityChecker {
        val injector = parentInjector.createChildInjector(ThetaBackendModule())
        if (config.soundness == SoundnessDirection.OverApproximating) {
            return injector.getInstance(ThetaHornCapabilityChecker::class.java)
        }
        return injector.getInstance(ThetaCapabilityChecker::class.java)
    }
}

class ThetaBackendModule : AbstractModule() {
    override fun configure() {
        bindScope(VerificationScoped::class.java, VerificationScope)
        install(FactoryModuleBuilder().build(ThetaVerificationContext.Factory::class.java))

        super.configure()
    }
}

class ThetaVerificationContext @AssistedInject constructor(
    @param:Assisted private val config: ThetaConfig,
    @param:Assisted override val executor: ThetaXstsExecutor,
    @Assisted request: BackendVerificationRequest,
    @Assisted capabilityChecker: BackendCapabilityChecker,
    private val thetaModelTransformer: ThetaModelTransformer,
    private val xstsModelCreator: XstsModelCreator,
    private val thetaLogVerdictParser: ThetaLogVerdictParser,
    private val cexReader: CexReader,
    private val cexWitnessTransformer: CexWitnessTransformer,
    private val xstsWitnessTransformer: XstsWitnessTransformer,
    private val inlinedWitnessTransformer: InlinedWitnessTransformer,
) : VerificationContext<ThetaArtifacts, ThetaVerdict>(
    backendId = "theta:${config.id}",
    configuredSoundness = config.soundness,
    request = request,
    capabilityChecker = capabilityChecker,
) {

    private val artifactManager = ThetaArtifactManager(request.artifactOutputPath)

    override suspend fun prepare(): ThetaArtifacts {
        currentCoroutineContext().ensureActive()
        logger.info { "[$backendId] transforming InlinedOxsts -> XSTS" }
        val artifacts = thetaModelTransformer.transform(request.inlinedOxsts, artifactManager.xstsUri)
        currentCoroutineContext().ensureActive()
        xstsModelCreator.save(artifacts.xsts)
        return artifacts
    }

    override suspend fun run(artifacts: ThetaArtifacts): ThetaVerdict? {
        currentCoroutineContext().ensureActive()
        val workingDir = artifactManager.xstsFile.parentFile
        val cexRelPath = artifactManager.cexFile.relativeTo(workingDir).path
        val command = config.parameters.split(" ") + listOf("--model", artifactManager.xstsFile.name, "--cexfile", cexRelPath)

        val spec = ThetaExecutionSpecification(
            workingDirectory = workingDir,
            command = command,
            logFile = artifactManager.logFile,
            errorFile = artifactManager.errorFile,
        )

        logger.debug { "[$backendId] invoking Theta: ${config.parameters}" }
        val result = executor.execute(spec)

        if (result.exitCode != 0) {
            error("Theta execution failed with exit code ${result.exitCode}")
        }

        return spec.logFile?.takeIf { it.exists() }?.useLines {
            thetaLogVerdictParser.parse(it)
        }
    }

    override fun interpret(rawVerdict: ThetaVerdict?, artifacts: ThetaArtifacts): VerificationVerdict {
        val property = request.inlinedOxsts.property.expression
        return when {
            property is AG && rawVerdict == ThetaVerdict.Unsafe -> VerificationVerdict.Failed
            property is EF && rawVerdict == ThetaVerdict.Safe -> VerificationVerdict.Failed
            rawVerdict == ThetaVerdict.Safe || rawVerdict == ThetaVerdict.Unsafe -> VerificationVerdict.Passed
            else -> VerificationVerdict.Inconclusive
        }
    }

    override suspend fun buildWitness(rawVerdict: ThetaVerdict?, artifacts: ThetaArtifacts): InlinedWitness? {
        if (!artifactManager.cexFile.exists()) {
            return null
        }
        return buildInlinedWitness(artifacts, artifactManager.cexFile.toPath())
    }

    private fun buildInlinedWitness(artifacts: ThetaArtifacts, cexPath: Path): InlinedWitness {
        logger.info { "[$backendId] building the inlined witness from CEX" }
        val cexModel = cexReader.loadCexModel(cexPath)
        val cexWitness = cexWitnessTransformer.transform(cexModel)
        val xstsWitness = xstsWitnessTransformer.transform(artifacts.xsts, cexWitness)
        val witness = inlinedWitnessTransformer.transform(request.inlinedOxsts, xstsWitness, artifacts.addedNames)
        return artifacts.wholeDelay?.restore(witness) ?: witness
    }

    interface Factory {
        fun create(
            config: ThetaConfig,
            executor: ThetaXstsExecutor,
            request: BackendVerificationRequest,
            capabilityChecker: BackendCapabilityChecker,
        ): ThetaVerificationContext
    }
}
