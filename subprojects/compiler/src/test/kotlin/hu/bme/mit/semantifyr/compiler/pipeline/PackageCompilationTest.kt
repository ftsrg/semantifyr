/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Provider
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.testing.util.ParseHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

@InjectWithOxsts
class PackageCompilationTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var packageParseHelper: ParseHelper<OxstsModelPackage>

    @Inject
    private lateinit var modelParseHelper: ParseHelper<EObject>

    @Inject
    private lateinit var resourceSetProvider: Provider<XtextResourceSet>

    @Test
    fun `case uses a class declared in another file of its package`() {
        val resourceSet = resourceSetProvider.get()
        packageParseHelper.parse(
            """
                package compilation::tests::house
                class Lamp {
                    var on: bool := false
                    tran toggle() {
                        on := !on
                    }
                }
            """.trimIndent(),
            resourceSet,
        )
        val cases = packageParseHelper.parse(
            """
                package compilation::tests::house
                @VerificationCase
                class LampCanBeOn {
                    contains lamp: Lamp
                    redefine tran {
                        inline lamp.toggle()
                    }
                    prop {
                        return EF lamp.on
                    }
                }
            """.trimIndent(),
            resourceSet,
        )
        EcoreUtil.resolveAll(resourceSet)
        assertThat(cases.eResource().errors).isEmpty()

        val compiled = compile(cases.declarations.filterIsInstance<ClassDeclaration>().single())

        assertThat(
            compiled.variables.map {
                it.name
            },
        ).singleElement().asString().contains("on")
    }

    @Test
    fun `case in an unnamed package compiles and its inlined model names it by its simple name`() {
        val resourceSet = resourceSetProvider.get()
        val snippet = packageParseHelper.parse(
            """
                enum Mode {
                    Off,
                    On,
                }
                @VerificationCase
                class Switch {
                    var mode: Mode := Mode::Off
                    redefine tran {
                        mode := Mode::On
                    }
                    prop {
                        return EF mode == Mode::On
                    }
                }
            """.trimIndent(),
            resourceSet,
        )
        EcoreUtil.resolveAll(resourceSet)
        assertThat(snippet.eResource().errors).isEmpty()
        val switch = snippet.declarations.filterIsInstance<ClassDeclaration>().single()

        compile(switch)

        val inlinedText = temporaryDirectory.resolve("artifacts").resolve("pipeline").resolve("inlined.oxsts").readText()
        assertThat(inlinedText).startsWith("inlined oxsts of Switch")
        val reparsed = modelParseHelper.parse(inlinedText, resourceSet) as InlinedOxsts
        EcoreUtil.resolveAll(reparsed)
        assertThat(reparsed.eResource().errors).isEmpty()
        assertThat(reparsed.classDeclaration).isSameAs(switch)
    }

    private fun compile(classDeclaration: ClassDeclaration): InlinedOxsts {
        val compiler = SemantifyrCompiler(
            injector = injector,
            artifactConfig = ArtifactConfig.ALL,
        )
        val artifactDirectory = temporaryDirectory.resolve("artifacts").also {
            Files.createDirectories(it)
        }
        return compiler.compile(classDeclaration, artifactDirectory).inlinedOxsts
    }
}
