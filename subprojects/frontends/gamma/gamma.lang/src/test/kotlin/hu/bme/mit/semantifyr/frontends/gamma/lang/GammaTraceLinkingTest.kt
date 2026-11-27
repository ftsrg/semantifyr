/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaRoot
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.diagnostics.Diagnostic
import org.eclipse.xtext.resource.XtextResource
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.testing.util.ParseHelper
import org.eclipse.xtext.testing.validation.ValidationTestHelper
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText

@InjectWithGamma
class GammaTraceLinkingTest {

    @Inject
    private lateinit var parseHelper: ParseHelper<GammaRoot>

    @Inject
    private lateinit var validationTestHelper: ValidationTestHelper

    @Inject
    private lateinit var resourceSetProvider: Provider<XtextResourceSet>

    @Test
    fun `a trace file links against its model in the resource set`() {
        val resourceSet = resourceSetProvider.get()
        val model = resourceSet.load(CROSSROADS)
        val cases = resourceSet.load(CROSSROADS_CASES)
        val trace = resourceSet.load(CROSSROADS_TRACE)

        validationTestHelper.assertNoErrors(model)
        validationTestHelper.assertNoErrors(cases)
        validationTestHelper.assertNoErrors(trace)
    }

    @Test
    fun `a trace parsed from its text links against the model in the resource set`() {
        val resourceSet = resourceSetProvider.get()
        resourceSet.load(CROSSROADS)
        resourceSet.load(CROSSROADS_CASES)

        val trace = parseHelper.parse(CROSSROADS_TRACE.readText(), URI.createURI("trace.get"), resourceSet)

        validationTestHelper.assertNoErrors(trace)
    }

    @Test
    fun `a trace follows the case it traces when the model changes`() {
        val resourceSet = resourceSetProvider.get()
        resourceSet.load(CROSSROADS)
        val cases = resourceSet.load(CROSSROADS_CASES)
        val trace = resourceSet.load(CROSSROADS_TRACE)
        val text = CROSSROADS_CASES.readText()
        validationTestHelper.assertNoErrors(trace)

        cases.reparse(text.replace("case ACanBeGreen", "case ACanBeGreenRenamed"))
        trace.relink()
        validationTestHelper.assertError(trace, GammaPackage.Literals.EXECUTION_TRACE, Diagnostic.LINKING_DIAGNOSTIC)

        cases.reparse(text)
        trace.relink()
        validationTestHelper.assertNoErrors(trace)
    }

    @Test
    fun `a trace of an unknown case reports an error`() {
        val resourceSet = resourceSetProvider.get()
        resourceSet.load(CROSSROADS)

        val trace = parseHelper.parse(
            """
            import Crossroads

            trace UnknownTrace of NoSuchCase
            """.trimIndent(),
            URI.createURI("Unknown.get"),
            resourceSet,
        )

        validationTestHelper.assertError(trace, GammaPackage.Literals.EXECUTION_TRACE, Diagnostic.LINKING_DIAGNOSTIC)
    }

    private fun XtextResourceSet.load(path: Path): XtextResource {
        return getResource(URI.createFileURI(path.absolutePathString()), true) as XtextResource
    }

    companion object {
        private val RESOURCES = Path("src", "test", "resources", "hu", "bme", "mit", "semantifyr", "frontends", "gamma", "lang", "GammaTraceLinkingTest")
        private val CROSSROADS = RESOURCES.resolve("Crossroads.gamma")
        private val CROSSROADS_CASES = RESOURCES.resolve("CrossroadsFurtherCases.gamma")
        private val CROSSROADS_TRACE = RESOURCES.resolve("crossroads-ACanBeGreen.trace.get")
    }
}
