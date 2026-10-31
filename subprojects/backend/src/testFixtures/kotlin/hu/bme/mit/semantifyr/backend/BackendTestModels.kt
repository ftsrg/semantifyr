/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.TypeLiteral
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.testing.util.ParseHelper
import java.nio.file.Path
import kotlin.io.path.Path

class BackendTestModels {
    companion object {
        const val COUNTER_EXCEEDS_BOUND = """
            inlined oxsts of semantifyr::Anything
            var x : int := 0
            init { }
            tran { x := x + 1 }
            prop { AG x < 2 }
        """

        const val UNREACHABLE_TARGET = """
            inlined oxsts of semantifyr::Anything
            var x : int := 0
            init { }
            tran { assume(x < 3) x := x + 1 }
            prop { EF x == 5 }
        """
    }

    val injector: Injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()

    private val loader = injector.getInstance(SemantifyrLoader::class.java)
    private val compiler = SemantifyrCompiler(injector, ArtifactConfig.NONE)
    private val resourceSetProvider = injector.getProvider(XtextResourceSet::class.java)
    private val inlinedParser = injector.getInstance(Key.get(object : TypeLiteral<ParseHelper<InlinedOxsts>>() {}))
    private val packageParser = injector.getInstance(Key.get(object : TypeLiteral<ParseHelper<OxstsModelPackage>>() {}))

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
        val context = loader.loadStandaloneModel(Path("build/test-models/semantic/$file.oxsts"))
        val classDeclaration = context.modelResources.single().allContents.asSequence().filterIsInstance<ClassDeclaration>().single {
            it.name == className
        }
        return compiler.compile(classDeclaration, outputDirectory).inlinedOxsts
    }
}
