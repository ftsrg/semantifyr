/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationConfigModule
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.OxstsInstantiator
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.utils.normalizedFixtureSource
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.serializer.ISerializer
import java.nio.file.Files

@InjectWithOxsts
abstract class ExpanderTestBase {

    @Inject
    protected lateinit var parseHelper: InlinedOxstsParseHelper

    @Inject
    protected lateinit var injector: Injector

    @Inject
    protected lateinit var serializer: ISerializer

    protected fun prepare(source: String): Prepared {
        val inlined = parseHelper.parse(source.normalizedFixtureSource())

        val compilationInjector = injector.createChildInjector(
            CompilationConfigModule(
                ArtifactConfig.none(Files.createTempDirectory("expander-test-")),
                OptimizationConfig.ALL,
            ),
            CompilationModule(inlined),
        )
        val instantiator = compilationInjector.getInstance(OxstsInstantiator::class.java)
        val context = instantiator.instantiate(CreatedCompilationContext(inlined))

        return Prepared(inlined, context, compilationInjector)
    }

    protected data class Prepared(
        val inlinedOxsts: InlinedOxsts,
        val context: InstantiatedCompilationContext,
        val compilationInjector: Injector,
    ) {
        val rootInstance: Instance get() = context.rootInstance
    }

    protected fun assertSerializedModelEquals(actualInlinedOxsts: InlinedOxsts, expectedSource: String) {
        val expectedInlined = parseHelper.parse(expectedSource.normalizedFixtureSource())
        val expectedText = serializer.serializeFormatted(expectedInlined)
        val actualText = serializer.serializeFormatted(actualInlinedOxsts)
        assertThat(actualText)
            .`as`("expander result should match expected model")
            .isEqualTo(expectedText)
    }
}
