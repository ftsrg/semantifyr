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

abstract class CompilerBenchmark<R> : InJvmBenchmark<CompilerCase, R>() {

    protected val outputDirectory = Path("build", "benchmark-artifacts")

    protected lateinit var caseClass: ClassDeclaration

    protected lateinit var creator: InlinedOxstsModelCreator

    protected lateinit var sizeCounter: ModelSizeCounter

    protected lateinit var instantiator: OxstsInstantiator

    protected lateinit var inliner: OxstsInliner

    protected lateinit var flattener: OxstsFlattener

    private lateinit var injector: Injector

    override fun setUp(configuration: CompilerCase) {
        super.setUp(configuration)
        val rootInjector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        val modelDirectory = Path("build", "benchmark-models")
        val context = rootInjector.getInstance(SemantifyrLoader::class.java).startContext()
            .loadLibraries(modelDirectory.resolve("library"))
            .loadModel(modelDirectory.resolve("${configuration.model}.semantifyr"))
            .buildAndResolve()
        caseClass = context.modelResources.single().allContents.asSequence().filterIsInstance<ClassDeclaration>().single {
            it.name == configuration.case
        }
        injector = rootInjector.createChildInjector(CompilationModule(ArtifactConfig.NONE, configuration.optimization))
        creator = injector.getInstance(InlinedOxstsModelCreator::class.java)
        sizeCounter = injector.getInstance(ModelSizeCounter::class.java)
    }

    // the phases' components hold compilation-scoped state, so they are fetched in the scope before the author's spans
    protected fun <T> inCompilationScope(created: CreatedCompilationContext, block: () -> T): T {
        try {
            return withCompilationScopeBlocking(CompilationRequest(inlinedOxsts = created.inlinedOxsts, outputDirectory = outputDirectory)) {
                instantiator = injector.getInstance(OxstsInstantiator::class.java)
                inliner = injector.getInstance(OxstsInliner::class.java)
                flattener = injector.getInstance(OxstsFlattener::class.java)
                block()
            }
        } finally {
            caseClass.eResource().resourceSet.resources.remove(created.inlinedOxsts.eResource())
        }
    }
}
