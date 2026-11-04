/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Provider
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
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
    private val injector: Injector,
    private val loader: SemantifyrLoader,
    private val resourceSetProvider: Provider<XtextResourceSet>,
    private val inlinedParser: ParseHelper<InlinedOxsts>,
    private val packageParser: ParseHelper<OxstsModelPackage>,
) {
    companion object {
        const val COUNTER_EXCEEDS_BOUND = """
            inlined oxsts of semantifyr::Anything
            var counter : int := 0
            init { }
            tran { counter := counter + 1 }
            prop { AG counter < 2 }
        """

        const val SATURATING_COUNTER_EXCEEDS_BOUND = """
            inlined oxsts of semantifyr::Anything
            var counter : int := 0
            init { }
            tran { if (counter < 3) { counter := counter + 1 } }
            prop { AG counter < 2 }
        """

        const val UNREACHABLE_TARGET = """
            inlined oxsts of semantifyr::Anything
            var counter : int := 0
            init { }
            tran { assume(counter < 3) counter := counter + 1 }
            prop { EF counter == 5 }
        """

        const val SATURATING_ONE_OF_SEVERAL_CHANGES = """
            inlined oxsts of semantifyr::Anything
            var counter : int := 0
            var unchanged : int := 5
            var flag : bool := true
            init { }
            tran { if (counter < 3) { counter := counter + 1 } }
            prop { AG counter < 2 }
        """

        const val STATELESS_VIOLATION = """
            inlined oxsts of semantifyr::Anything
            init { }
            tran { choice { } or { assume (true) } }
            prop { AG false }
        """
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

    fun compile(
        file: String,
        className: String,
        outputDirectory: Path,
    ): InlinedOxsts {
        return compileFlattened(file, className, outputDirectory).inlinedOxsts
    }

    fun compileFlattened(
        file: String,
        className: String,
        outputDirectory: Path,
    ): FlattenedCompilationContext {
        val context = loader.loadStandaloneModel(Path("build/test-models/semantic/$file.oxsts"))
        val classDeclaration = context.modelResources.single().allContents.asSequence().filterIsInstance<ClassDeclaration>().single {
            it.name == className
        }
        return SemantifyrCompiler(injector, ArtifactConfig.NONE).compile(classDeclaration, outputDirectory)
    }
}
