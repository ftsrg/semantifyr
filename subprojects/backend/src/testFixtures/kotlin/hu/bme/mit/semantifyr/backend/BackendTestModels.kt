/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.compiler.SemantifyrCompilerFactory
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.testing.util.ParseHelper
import java.nio.file.Path
import kotlin.io.path.Path

class BackendTestModels @Inject constructor(
    private val compilerFactory: SemantifyrCompilerFactory,
    private val loader: SemantifyrLoader,
    private val resourceSetProvider: Provider<XtextResourceSet>,
    private val inlinedParser: ParseHelper<InlinedOxsts>,
    private val packageParser: ParseHelper<OxstsModelPackage>,
) {
    companion object {
        val SATURATING_COUNTER_EXCEEDS_BOUND = model("saturating_counter_exceeds_bound.semantifyr")

        val UNREACHABLE_TARGET = model("unreachable_target.semantifyr")

        val SATURATING_ONE_OF_SEVERAL_CHANGES = model("saturating_one_of_several_changes.semantifyr")

        private fun model(name: String): String {
            return checkNotNull(BackendTestModels::class.java.getResource("BackendTestModels/$name")).readText()
        }
    }

    fun parse(source: String, vararg packages: String): InlinedOxsts {
        val resourceSet = resourceSetProvider.get()
        for (oxstsPackage in packages) {
            packageParser.parse(oxstsPackage.trimIndent(), resourceSet)
        }
        val inlinedOxsts = inlinedParser.parse(source.trimIndent(), resourceSet)
        EcoreUtil.resolveAll(inlinedOxsts)
        return inlinedOxsts
    }

    fun compileFlattened(
        file: String,
        className: String,
        outputDirectory: Path,
    ): FlattenedCompilationContext {
        val semanticModels = System.getProperty("semantifyr.semanticModels") ?: error("The build names no semantic test models")
        val context = loader.loadStandaloneModel(Path(semanticModels, "$file.semantifyr"))
        val classDeclaration = context.modelResources.single().allContents.asSequence().filterIsInstance<ClassDeclaration>().single {
            it.name == className
        }
        return compilerFactory.create(ArtifactConfig.NONE, OptimizationConfig.ALL).compile(classDeclaration, outputDirectory)
    }
}
