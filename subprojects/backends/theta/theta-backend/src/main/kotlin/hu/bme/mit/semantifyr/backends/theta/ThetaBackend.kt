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
import hu.bme.mit.semantifyr.backends.theta.artifacts.ThetaArtifactManager
import hu.bme.mit.semantifyr.backends.theta.backannotation.CexReader
import hu.bme.mit.semantifyr.backends.theta.backannotation.CexWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.InlinedWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.XstsWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.ThetaModelTransformer
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
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
            val context = factory.create(config, executor, request)
            context.execute()
        }
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
    private val thetaModelTransformer: ThetaModelTransformer,
    private val cexReader: CexReader,
    private val cexWitnessTransformer: CexWitnessTransformer,
    private val xstsWitnessTransformer: XstsWitnessTransformer,
    private val inlinedWitnessTransformer: InlinedWitnessTransformer,
) : VerificationContext<XstsModel, ThetaVerificationContext.ThetaVerdict>(
    backendId = "theta:${config.id}",
    request = request,
) {

    private val artifactManager = ThetaArtifactManager(request.artifactOutputPath)

    override suspend fun prepare(): XstsModel {
        logger.info { "[$backendId] transforming InlinedOxsts -> XSTS" }
        val xsts = thetaModelTransformer.transform(request.inlinedOxsts, artifactManager.xstsUri)

        val resourceSet = request.inlinedOxsts.eResource().resourceSet
        resourceSet.getResource(artifactManager.xstsUri, false)?.delete(emptyMap<Any, Any>())
        val resource = resourceSet.createResource(artifactManager.xstsUri)
        resource.contents += xsts
        xsts.eResource().save(emptyMap<Any, Any>())

        return xsts
    }

    override suspend fun run(artifacts: XstsModel): ThetaVerdict? {
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

        return spec.logFile?.takeIf { it.exists() }?.useLines { lines ->
            lines.firstNotNullOfOrNull(::detectSafety)
        }
    }

    override fun interpret(rawVerdict: ThetaVerdict?, artifacts: XstsModel): VerificationVerdict {
        val property = request.inlinedOxsts.property.expression
        return when {
            property is AG && rawVerdict == ThetaVerdict.Unsafe -> VerificationVerdict.Failed
            property is EF && rawVerdict == ThetaVerdict.Safe -> VerificationVerdict.Failed
            rawVerdict == ThetaVerdict.Safe || rawVerdict == ThetaVerdict.Unsafe -> VerificationVerdict.Passed
            else -> VerificationVerdict.Inconclusive
        }
    }

    override suspend fun buildWitness(rawVerdict: ThetaVerdict?, artifacts: XstsModel): InlinedWitness? {
        if (!artifactManager.cexFile.exists()) {
            return null
        }
        return buildInlinedWitness(artifacts, artifactManager.cexFile.toPath())
    }

    private fun buildInlinedWitness(xsts: XstsModel, cexPath: Path): InlinedWitness {
        logger.info { "[$backendId] building inlined-oxsts witness from CEX" }
        val cexModel = cexReader.loadCexModel(cexPath)
        val cexWitness = cexWitnessTransformer.transform(cexModel)
        val xstsWitness = xstsWitnessTransformer.transform(xsts, cexWitness)
        return inlinedWitnessTransformer.transform(request.inlinedOxsts, xstsWitness)
    }

    private fun detectSafety(line: String): ThetaVerdict? {
        return when {
            line.contains("SafetyResult Unsafe") -> ThetaVerdict.Unsafe
            line.contains("SafetyResult Safe") -> ThetaVerdict.Safe
            else -> null
        }
    }

    enum class ThetaVerdict { Safe, Unsafe }

    interface Factory {
        fun create(
            config: ThetaConfig,
            executor: ThetaXstsExecutor,
            request: BackendVerificationRequest,
        ): ThetaVerificationContext
    }
}
