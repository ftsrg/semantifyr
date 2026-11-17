/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.benchmark.MeasuredProcesses
import hu.bme.mit.semantifyr.benchmark.ProcessBenchmark
import hu.bme.mit.semantifyr.benchmark.commandOutput
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.verifier.internal.TrivialVerifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.time.Clock

@Serializable
data class CaseModel(
    val library: String?,
    val file: String,
    val case: String,
)

@Serializable
data class ThetaResult(
    val verdict: VerificationVerdict,
    val message: String?,
)

// one injector per JVM, since Xtext injectors are not safe to create twice
private val injector by lazy {
    OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
}

abstract class ThetaBenchmark<C> : ProcessBenchmark<C, ThetaResult>() {

    open val thetaConfig = ThetaConfig.CegarExpl

    private lateinit var compiled: FlattenedCompilationContext

    abstract fun compile(configuration: C): FlattenedCompilationContext

    override fun backendVersions(): Map<String, String> {
        return mapOf(
            "theta-xsts-cli" to commandOutput("theta-xsts-cli", "--version").lines().last(),
        )
    }

    override fun setUp(configuration: C) {
        super.setUp(configuration)
        compiled = compile(configuration)
    }

    override fun repetition(processes: MeasuredProcesses): ThetaResult {
        val trivialVerifier = injector.getInstance(TrivialVerifier::class.java)
        if (trivialVerifier.isTriviallyVerifiable(compiled.inlinedOxsts)) {
            val result = trivialVerifier.verifyTrivially(compiled.inlinedOxsts, Clock.System.now())
            return ThetaResult(result.verdict, result.message)
        }
        val environment = ExecutionEnvironment.builder()
            .put(ThetaExecutorKey) {
                RunexecThetaXstsExecutor(processes)
            }
            .build()
        val request = BackendVerificationRequest(
            inlinedOxsts = compiled.inlinedOxsts,
            artifactOutputPath = processes.directory,
            flatteningInfo = compiled.flatteningInfo,
        )
        val result = runBlocking {
            ThetaBackend().verify(injector, thetaConfig, request, environment)
        }
        return ThetaResult(result.verdict, result.message)
    }

    protected fun compile(model: CaseModel, optimization: OptimizationConfig): FlattenedCompilationContext {
        var context = injector.getInstance(SemantifyrLoader::class.java).startContext()
        if (model.library != null) {
            context = context.loadLibraries(Path(model.library))
        }
        val caseClass = context.loadModel(Path(model.file)).buildAndResolve().modelResources.single().allContents.asSequence().filterIsInstance<ClassDeclaration>().single {
            it.name == model.case
        }
        return SemantifyrCompiler(injector, ArtifactConfig.NONE, optimization).compile(caseClass, createTempDirectory("theta-benchmark"))
    }
}
