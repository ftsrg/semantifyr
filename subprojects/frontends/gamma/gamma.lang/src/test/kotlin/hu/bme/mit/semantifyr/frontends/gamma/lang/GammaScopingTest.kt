/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaRoot
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StatechartDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import hu.bme.mit.semantifyr.frontends.gamma.lang.validation.GammaValidator
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.testing.util.ParseHelper
import org.eclipse.xtext.testing.validation.ValidationTestHelper
import org.junit.jupiter.api.Test

@InjectWithGamma
class GammaScopingTest {

    @Inject
    private lateinit var parseHelper: ParseHelper<GammaRoot>

    @Inject
    private lateinit var validationTestHelper: ValidationTestHelper

    @Inject
    private lateinit var resourceSetProvider: Provider<XtextResourceSet>

    private fun parseAll(vararg texts: String): List<GammaModelPackage> {
        val resourceSet = resourceSetProvider.get()
        val roots = texts.mapIndexed { index, text ->
            parseHelper.parse(text.trimIndent(), URI.createURI("file$index.gamma"), resourceSet) as GammaModelPackage
        }
        EcoreUtil.resolveAll(resourceSet)
        return roots
    }

    private val controlPackage = """
        package library::control
        interface Control {
            in event start
        }
    """

    private fun machine(imports: String): String {
        return """
            package test::machine
            $imports
            statechart Machine [
                port control : provides Control
            ] {
            }
        """
    }

    @Test
    fun `an import names a package by its qualified name and brings its declarations in`() {
        val (_, machine) = parseAll(controlPackage, machine("import library::control"))

        validationTestHelper.assertNoErrors(machine)
        val port = (machine.components.single() as StatechartDeclaration).ports.single()
        assertThat(port.`interface`.eIsProxy()).isFalse()
    }

    @Test
    fun `a declaration of another package is not visible without its import`() {
        val (_, machine) = parseAll(controlPackage, machine(""))

        val port = (machine.components.single() as StatechartDeclaration).ports.single()
        assertThat(port.`interface`.eIsProxy()).isTrue()
    }

    @Test
    fun `a name two imports declare is ambiguous`() {
        val otherControlPackage = controlPackage.replace("library::control", "other::control")
        val (_, _, machine) = parseAll(controlPackage, otherControlPackage, machine("import library::control\nimport other::control"))

        validationTestHelper.assertError(machine, GammaPackage.Literals.PORT_DECLARATION, GammaValidator.AMBIGUOUS_REFERENCE)
    }

    @Test
    fun `a package spans the files that declare it`() {
        val (_, machine) = parseAll(controlPackage.replace("library::control", "test::machine"), machine(""))

        validationTestHelper.assertNoErrors(machine)
    }

    @Test
    fun `a port named like its interface links the interface`() {
        val (_, machine) = parseAll(
            controlPackage,
            """
                package test::machine
                import library::control
                statechart Machine [
                    port Control : provides Control
                ] {
                }
            """,
        )

        validationTestHelper.assertNoErrors(machine)
    }
}
