/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import com.google.inject.Injector
import hu.bme.mit.semantifyr.benchmark.InJvmBenchmark
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import hu.bme.mit.semantifyr.compiler.pipeline.InlinedOxstsModelCreator
import hu.bme.mit.semantifyr.compiler.pipeline.ModelSizeCounter
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.OxstsFlattener
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.OxstsInliner
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.OxstsInstantiator
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.scopes.withCompilationScopeBlocking
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import kotlinx.serialization.Serializable
import kotlin.io.path.Path

@Serializable
data class CompilerCase(
    val model: String,
    val case: String,
    val optimization: OptimizationConfig,
)

class CompilationPhases(
    val instantiator: OxstsInstantiator,
    val inliner: OxstsInliner,
    val flattener: OxstsFlattener,
)

abstract class CompilerBenchmark<R> : InJvmBenchmark<CompilerCase, R>() {

    protected val outputDirectory = Path("build", "benchmark-artifacts")

    protected lateinit var caseClass: ClassDeclaration

    protected lateinit var creator: InlinedOxstsModelCreator

    protected lateinit var sizeCounter: ModelSizeCounter

    private lateinit var injector: Injector

    override fun setUp(configuration: CompilerCase) {
        super.setUp(configuration)
        val rootInjector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        val modelDirectory = Path("build", "benchmark-models")
        val context = rootInjector.getInstance(SemantifyrLoader::class.java).startContext()
            .loadLibraries(modelDirectory.resolve("library"))
            .loadModel(modelDirectory.resolve("${configuration.model}.semantifyr"))
            .buildAndResolve()
        val classes = context.modelResources.single().allContents.asSequence().filterIsInstance<ClassDeclaration>()
        caseClass = classes.single {
            it.name == configuration.case
        }
        injector = rootInjector.createChildInjector(CompilationModule(ArtifactConfig.NONE, configuration.optimization))
        creator = injector.getInstance(InlinedOxstsModelCreator::class.java)
        sizeCounter = injector.getInstance(ModelSizeCounter::class.java)
    }

    // the phases hold compilation-scoped state, so they exist only inside the scope
    protected fun <T> inCompilationScope(created: CreatedCompilationContext, block: CompilationPhases.() -> T): T {
        val request = CompilationRequest(inlinedOxsts = created.inlinedOxsts, outputDirectory = outputDirectory)
        try {
            return withCompilationScopeBlocking(request) {
                val phases = CompilationPhases(
                    instantiator = injector.getInstance(OxstsInstantiator::class.java),
                    inliner = injector.getInstance(OxstsInliner::class.java),
                    flattener = injector.getInstance(OxstsFlattener::class.java),
                )
                phases.block()
            }
        } finally {
            caseClass.eResource().resourceSet.resources.remove(created.inlinedOxsts.eResource())
        }
    }
}
