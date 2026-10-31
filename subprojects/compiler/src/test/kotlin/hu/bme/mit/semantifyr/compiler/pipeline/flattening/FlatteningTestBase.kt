/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.utils.normalizedFixtureSource
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.serializer.ISerializer
import org.eclipse.xtext.validation.CheckMode
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@InjectWithOxsts
abstract class FlatteningTestBase {

    @TempDir
    lateinit var tempDir: Path

    @Inject
    private lateinit var parseHelper: OxstsPackageParseHelper

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var serializer: ISerializer

    protected fun flatten(
        className: String,
        source: String,
        optimization: OptimizationConfig = OptimizationConfig.NONE,
    ): FlattenedCompilationContext {
        val parsed = parseHelper.parse(source.normalizedFixtureSource(), CheckMode.ALL)
        parsed.assertNoResourceErrors()
        parsed.assertNoValidationErrors()
        val classDeclaration = EcoreUtil2.eAllOfType(parsed.oxstsPackage, ClassDeclaration::class.java).single {
            it.name == className
        }
        return SemantifyrCompiler(injector, ArtifactConfig.NONE, optimization).compile(classDeclaration, tempDir)
    }

    protected fun serialize(eObject: EObject): String {
        return serializer.serializeFormatted(eObject)
    }

    protected fun FlattenedCompilationContext.variableNamed(name: String): VariableDeclaration {
        return inlinedOxsts.variables.single {
            it.name == name
        }
    }

}
