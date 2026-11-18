/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import com.google.inject.Injector
import hu.bme.mit.semantifyr.benchmark.Spans
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import hu.bme.mit.semantifyr.compiler.pipeline.InlinedOxstsModelCreator
import hu.bme.mit.semantifyr.compiler.pipeline.ModelSizeCounter
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
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
data class ModelCase(
    val library: String?,
    val file: String,
    val case: String,
)

class CompiledPhases(
    val inlinedSize: ModelSize,
    val flattenedSize: ModelSize,
    val flattened: FlattenedCompilationContext,
)

// one injector per JVM, since Xtext injectors are not safe to create twice
val oxstsInjector: Injector by lazy {
    OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
}

class CaseCompilation(
    model: ModelCase,
    private val optimization: OptimizationConfig,
) {
    private val outputDirectory = Path("build", "benchmark-artifacts")

    private val caseClass = load(model)

    private val injector = oxstsInjector.createChildInjector(CompilationModule(ArtifactConfig.NONE, optimization))

    private val creator = injector.getInstance(InlinedOxstsModelCreator::class.java)

    private val sizeCounter = injector.getInstance(ModelSizeCounter::class.java)

    private var previous: CreatedCompilationContext? = null

    fun whole(): FlattenedCompilationContext {
        return SemantifyrCompiler(oxstsInjector, ArtifactConfig.NONE, optimization).compile(caseClass, outputDirectory)
    }

    fun phases(spans: Spans): CompiledPhases {
        previous?.let {
            caseClass.eResource().resourceSet.resources.remove(it.inlinedOxsts.eResource())
        }
        val created = spans.measure("create") {
            creator.create(caseClass, outputDirectory)
        }
        previous = created
        val request = CompilationRequest(inlinedOxsts = created.inlinedOxsts, outputDirectory = outputDirectory)
        return withCompilationScopeBlocking(request) {
            val instantiator = injector.getInstance(OxstsInstantiator::class.java)
            val inliner = injector.getInstance(OxstsInliner::class.java)
            val flattener = injector.getInstance(OxstsFlattener::class.java)
            val instantiated = spans.measure("instantiation") {
                instantiator.instantiate(created)
            }
            val inlined = spans.measure("inlining") {
                inliner.inline(instantiated)
            }
            val inlinedSize = sizeCounter.count(inlined)
            val flattened = spans.measure("flattening") {
                flattener.flatten(inlined)
            }
            CompiledPhases(inlinedSize, sizeCounter.count(flattened.inlinedOxsts), flattened)
        }
    }

    private fun load(model: ModelCase): ClassDeclaration {
        var context = oxstsInjector.getInstance(SemantifyrLoader::class.java).startContext()
        if (model.library != null) {
            context = context.loadLibraries(Path(model.library))
        }
        val modelResource = context.loadModel(Path(model.file)).buildAndResolve().modelResources.single()
        return modelResource.allContents.asSequence().filterIsInstance<ClassDeclaration>().single {
            it.name == model.case
        }
    }
}
