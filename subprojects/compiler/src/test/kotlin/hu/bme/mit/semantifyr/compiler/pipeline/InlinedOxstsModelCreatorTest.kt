/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.serializer.ISerializer
import org.eclipse.xtext.testing.util.ParseHelper
import org.junit.jupiter.api.Test
import java.nio.file.Path

@InjectWithOxsts
class InlinedOxstsModelCreatorTest {
    @Inject
    private lateinit var parseHelper: ParseHelper<OxstsModelPackage>

    @Inject
    private lateinit var inlinedOxstsModelCreator: InlinedOxstsModelCreator

    @Inject
    private lateinit var serializer: ISerializer

    @Test
    fun `inlined model of a class in an unnamed package names it by its simple name`() {
        val snippet = parseHelper.parse(
            """
                @VerificationCase
                class Switch {
                    var on: bool := false
                    redefine tran {
                        on := true
                    }
                    prop {
                        return EF on
                    }
                }
            """.trimIndent(),
        )
        val switch = snippet.declarations.filterIsInstance<ClassDeclaration>().single()

        val inlinedOxsts = inlinedOxstsModelCreator.create(switch, Path.of("unnamed-package")).inlinedOxsts

        assertThat(serializer.serialize(inlinedOxsts)).startsWith("inlined oxsts of Switch")
    }
}
