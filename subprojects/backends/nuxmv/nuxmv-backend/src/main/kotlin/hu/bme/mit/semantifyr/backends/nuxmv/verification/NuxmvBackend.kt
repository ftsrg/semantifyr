/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.verification

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
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutionSpecification
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutor
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutorKey
import hu.bme.mit.semantifyr.backends.nuxmv.artifacts.NuxmvArtifactManager
import hu.bme.mit.semantifyr.backends.nuxmv.trace.NuxmvInlinedOxstsWitnessTransformer
import hu.bme.mit.semantifyr.backends.nuxmv.trace.NuxmvTraceParser
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvArtifacts
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvModelTransformer
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn

class NuxmvBackend : VerificationBackend<NuxmvConfig>() {
    override val id: String = "nuxmv"
    override val executorKey = NuxmvExecutorKey

    private val logger by loggerFactory()

    override suspend fun verify(
        parentInjector: Injector,
        config: NuxmvConfig,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        val executor = environment[NuxmvExecutorKey]
        logger.info { "[nuxmv:${config.id}] dispatching via ${executor::class.simpleName}" }

        val injector = parentInjector.createChildInjector(NuxmvBackendModule())
        return withVerificationScope {
            val factory = injector.getInstance(NuxmvVerificationContext.Factory::class.java)
            val context = factory.create(config, executor, request)
            context.execute()
        }
    }
}

internal class NuxmvBackendModule : AbstractModule() {
    override fun configure() {
        bindScope(VerificationScoped::class.java, VerificationScope)
        install(FactoryModuleBuilder().build(NuxmvVerificationContext.Factory::class.java))

        super.configure()
    }
}

class NuxmvVerificationContext @AssistedInject constructor(
    @param:Assisted private val config: NuxmvConfig,
    @param:Assisted override val executor: NuxmvExecutor,
    @Assisted request: BackendVerificationRequest,
    private val nuxmvModelTransformer: NuxmvModelTransformer,
    private val traceParser: NuxmvTraceParser,
    private val witnessTransformer: NuxmvInlinedOxstsWitnessTransformer,
) : VerificationContext<NuxmvArtifacts, NuxmvVerificationContext.NuxmvVerdict>(
    backendId = "nuxmv:${config.id}",
    request = request,
) {

    private val artifactManager = NuxmvArtifactManager(request.artifactOutputPath)

    override suspend fun prepare(): NuxmvArtifacts {
        logger.info { "[$backendId] generating SMV model and command files" }
        val artifacts = nuxmvModelTransformer.transform(request.inlinedOxsts)

        artifactManager.modelFile.apply {
            parentFile?.mkdirs()
        }.writeText(artifacts.smv)

        val checkCommand = """${config.checkCommand} "${artifacts.property.invariant}""""
        val cmdScript = buildString {
            appendLine("set on_failure_script_quits")
            appendLine("""set input_file "${artifactManager.modelFile.absolutePath}"""")
            appendLine(config.setupCommand)
            appendLine("set default_trace_plugin 1")
            appendLine(checkCommand)
            appendLine("quit")
        }
        artifactManager.commandFile.writeText(cmdScript)

        return artifacts
    }

    override suspend fun run(artifacts: NuxmvArtifacts): NuxmvVerdict? {
        val specification = NuxmvExecutionSpecification(
            workingDirectory = artifactManager.modelFile.parentFile,
            commandFile = artifactManager.commandFile,
            logFile = artifactManager.logFile,
            errorFile = artifactManager.errorFile,
        )

        val result = executor.execute(specification)

        if (result.exitCode != 0 && result.exitCode != 1) {
            error("nuXmv execution failed with exit code ${result.exitCode}")
        }

        return artifactManager.logFile.takeIf {
            it.exists()
        }?.useLines {
            detectVerdict(it)
        }
    }

    override fun interpret(rawVerdict: NuxmvVerdict?, artifacts: NuxmvArtifacts): VerificationVerdict {
        val verdict = if (artifacts.property.invertVerdict) {
            rawVerdict?.invert()
        } else {
            rawVerdict
        }
        return when (verdict) {
            NuxmvVerdict.True -> VerificationVerdict.Passed
            NuxmvVerdict.False -> VerificationVerdict.Failed
            null -> VerificationVerdict.Inconclusive
        }
    }

    override suspend fun buildWitness(rawVerdict: NuxmvVerdict?, artifacts: NuxmvArtifacts): InlinedWitness? {
        if (rawVerdict != NuxmvVerdict.False) {
            return null
        }
        val trace = traceParser.parse(artifactManager.logFile)
        if (trace == null) {
            logger.warn { "[$backendId] no trace parseable from ${artifactManager.logFile.name}. Witness will be omitted" }
            return null
        }
        logger.info { "[$backendId] parsed nuXmv trace with ${trace.states.size} state(s). Building witness" }
        return witnessTransformer.transform(request.inlinedOxsts, trace)
    }

    private fun detectVerdict(lines: Sequence<String>): NuxmvVerdict? {
        var inBlock = false
        for (raw in lines) {
            val lower = raw.lowercase()
            val opensBlock = lower.trimStart().startsWith("-- invariant") ||
                lower.trimStart().startsWith("-- specification")
            if (opensBlock) {
                if (lower.trimEnd().endsWith("is true")) {
                    return NuxmvVerdict.True
                }
                if (lower.trimEnd().endsWith("is false")) {
                    return NuxmvVerdict.False
                }
                inBlock = true
                continue
            }
            if (inBlock) {
                val trimmed = lower.trimEnd()
                if (trimmed.endsWith("is true")) {
                    return NuxmvVerdict.True
                }
                if (trimmed.endsWith("is false")) {
                    return NuxmvVerdict.False
                }
            }
        }
        return null
    }

    enum class NuxmvVerdict {
        True,
        False,
        ;

        fun invert(): NuxmvVerdict {
            return if (this == True) {
                False
            } else {
                True
            }
        }
    }

    interface Factory {
        fun create(
            config: NuxmvConfig,
            executor: NuxmvExecutor,
            request: BackendVerificationRequest,
        ): NuxmvVerificationContext
    }
}
