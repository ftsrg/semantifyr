/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.verification

import com.google.inject.AbstractModule
import com.google.inject.Injector
import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import com.google.inject.assistedinject.FactoryModuleBuilder
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
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
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutionSpecification
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutor
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutorKey
import hu.bme.mit.semantifyr.backends.uppaal.artifacts.UppaalArtifactManager
import hu.bme.mit.semantifyr.backends.uppaal.trace.UppaalInlinedOxstsWitnessTransformer
import hu.bme.mit.semantifyr.backends.uppaal.trace.UppaalLogVerdictParser
import hu.bme.mit.semantifyr.backends.uppaal.trace.UppaalTraceParser
import hu.bme.mit.semantifyr.backends.uppaal.trace.UppaalVerdict
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalArtifacts
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalModelTransformer
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalOperationVisitor
import hu.bme.mit.semantifyr.logging.info

class UppaalBackend : VerificationBackend<UppaalConfig>() {
    override val id = "uppaal"
    override val executorKey = UppaalExecutorKey

    override suspend fun verify(
        parentInjector: Injector,
        config: UppaalConfig,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        val executor = environment[UppaalExecutorKey]
        logger.info { "[uppaal:${config.id}] dispatching via ${executor::class.simpleName}" }

        val injector = parentInjector.createChildInjector(UppaalBackendModule())
        return withVerificationScope {
            val factory = injector.getInstance(UppaalVerificationContext.Factory::class.java)
            val context = factory.create(config, executor, request)
            context.execute()
        }
    }
}

class UppaalBackendModule : AbstractModule() {
    override fun configure() {
        bindScope(VerificationScoped::class.java, VerificationScope)
        install(FactoryModuleBuilder().build(UppaalVerificationContext.Factory::class.java))
        install(FactoryModuleBuilder().build(UppaalOperationVisitor.Factory::class.java))

        super.configure()
    }
}

class UppaalVerificationContext @AssistedInject constructor(
    @param:Assisted private val config: UppaalConfig,
    @param:Assisted override val executor: UppaalExecutor,
    @Assisted request: BackendVerificationRequest,
    private val uppaalModelTransformer: UppaalModelTransformer,
    private val uppaalTraceParser: UppaalTraceParser,
    private val uppaalLogVerdictParser: UppaalLogVerdictParser,
    private val uppaalInlinedOxstsWitnessTransformer: UppaalInlinedOxstsWitnessTransformer,
) : VerificationContext<UppaalArtifacts, UppaalVerdict>(
    backendId = "uppaal:${config.id}",
    request = request,
) {

    private val artifactManager = UppaalArtifactManager(request.artifactOutputPath)

    override suspend fun prepare(): UppaalArtifacts {
        logger.info { "[$backendId] generating Uppaal XML and query files" }
        val artifacts = uppaalModelTransformer.transform(request.inlinedOxsts)
        artifactManager.modelFile.apply {
            parentFile?.mkdirs()
        }.writeText(artifacts.modelXml)
        artifactManager.queryFile.writeText(artifacts.query + "\n")
        return artifacts
    }

    override suspend fun run(artifacts: UppaalArtifacts): UppaalVerdict? {
        val workingDir = artifactManager.modelFile.parentFile
        val params = config.parameters.takeIf { it.isNotBlank() }?.split(" ") ?: emptyList()
        val command = params + listOf(artifactManager.modelFile.name, artifactManager.queryFile.name)

        val spec = UppaalExecutionSpecification(
            workingDirectory = workingDir,
            command = command,
            logFile = artifactManager.logFile,
            errorFile = artifactManager.errorFile,
        )

        val result = executor.execute(spec)

        detectOutOfRangeError()

        if (result.exitCode != 0 && result.exitCode != 1) {
            error("verifyta execution failed with exit code ${result.exitCode}")
        }

        return artifactManager.logFile.takeIf {
            it.exists()
        }?.useLines {
            uppaalLogVerdictParser.parse(it)
        }
    }

    private fun detectOutOfRangeError() {
        val offending = artifactManager.errorFile.takeIf {
            it.exists()
        }?.useLines { lines ->
            lines.firstOrNull {
                it.contains("is out of range", ignoreCase = true)
            }
        } ?: return
        throw BackendUnsupportedException(
            "Uppaal cannot represent the OXSTS model: $offending. " +
                "Uppaal's bounded `int` cannot hold a value the model assigns, and no faithful " +
                "backend encoding is available without a tighter source bound.",
        )
    }

    override fun interpret(rawVerdict: UppaalVerdict?, artifacts: UppaalArtifacts): VerificationVerdict {
        return when (rawVerdict) {
            UppaalVerdict.Satisfied -> VerificationVerdict.Passed
            UppaalVerdict.Unsatisfied -> VerificationVerdict.Failed
            null -> VerificationVerdict.Inconclusive
        }
    }

    override suspend fun buildWitness(rawVerdict: UppaalVerdict?, artifacts: UppaalArtifacts): InlinedWitness? {
        if (rawVerdict == null) {
            return null
        }
        val trace = uppaalTraceParser.parse(artifactManager.logFile)
        if (trace == null) {
            logger.info { "[$backendId] no diagnostic trace in ${artifactManager.logFile.name}; witness not produced" }
            return null
        }
        logger.info { "[$backendId] parsed Uppaal trace with ${trace.states.size} state(s); building witness" }
        return uppaalInlinedOxstsWitnessTransformer.transform(request.inlinedOxsts, trace)
    }

    interface Factory {
        fun create(
            config: UppaalConfig,
            executor: UppaalExecutor,
            request: BackendVerificationRequest,
        ): UppaalVerificationContext
    }
}
