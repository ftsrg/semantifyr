/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.transformation

import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BackendNameManglerTest {

    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

    private fun variable(name: String): VariableDeclaration {
        return OxstsFactory.createVariableDeclaration().also {
            it.name = name
        }
    }

    private fun local(name: String): VariableDeclaration {
        return OxstsFactory.createLocalVarDeclarationOperation().also {
            it.name = name
        }
    }

    private fun enum(
        packageName: String,
        name: String,
        vararg literals: String,
    ): EnumDeclaration {
        val enumDeclaration = OxstsFactory.createEnumDeclaration().also {
            it.name = name
        }
        for (literal in literals) {
            enumDeclaration.literals += OxstsFactory.createEnumLiteral().also {
                it.name = literal
            }
        }
        OxstsFactory.createOxstsModelPackage().also {
            it.name = packageName
            it.declarations += enumDeclaration
        }
        return enumDeclaration
    }

    @Test
    fun `a separator and an underscore mangle to different names`() {
        val mangler = BackendNameMangler()
        val names = listOf(
            "p\$x",
            "p_x",
            "p\$\$x",
            "p__x",
            "p\$_x",
            "p_\$x",
            "\$\$root\$\$light",
        ).map {
            mangler.nameOf(variable(it))
        }

        assertThat(names).doesNotHaveDuplicates()
        assertThat(names).allMatch {
            identifier.matches(it)
        }
    }

    @Test
    fun `an instance path keeps its readable form`() {
        assertThat(BackendNameMangler().nameOf(variable("\$\$root\$\$light"))).isEqualTo("__root__light")
    }

    @Test
    fun `locals of one name mangle apart from each other and from every global`() {
        val mangler = BackendNameMangler()
        val first = mangler.nameOf(local("delta"))
        val second = mangler.nameOf(local("delta"))
        val global = mangler.nameOf(variable("delta"))

        assertThat(listOf(first, second, global)).doesNotHaveDuplicates()
        assertThat(mangler.nameOf(variable(first))).isNotIn(first, second)
    }

    @Test
    fun `a name maps back to its variable`() {
        val mangler = BackendNameMangler()
        val separated = variable("p\$x")
        val underscored = variable("p_x")
        mangler.nameOf(separated)
        mangler.nameOf(underscored)

        assertThat(mangler.variableOf(mangler.nameOf(separated))).isSameAs(separated)
        assertThat(mangler.variableOf(mangler.nameOf(underscored))).isSameAs(underscored)
    }

    @Test
    fun `enums of one name in two packages mangle apart`() {
        val mangler = BackendNameMangler()
        val first = enum("first", "Light", "On", "Off")
        val second = enum("second", "Light", "On", "Off")

        assertThat(mangler.nameOf(first)).isNotEqualTo(mangler.nameOf(second))
        assertThat(mangler.nameOf(first.literals[0])).isNotEqualTo(mangler.nameOf(second.literals[0]))
    }

    @Test
    fun `literals of one name in two enums mangle apart from each other and from variables`() {
        val mangler = BackendNameMangler()
        val light = enum("lights", "Light", "On", "Off")
        val switch = enum("lights", "Switch", "On", "Off")
        val names = listOf(
            mangler.nameOf(light),
            mangler.nameOf(switch),
            mangler.nameOf(light.literals[0]),
            mangler.nameOf(switch.literals[0]),
            mangler.nameOf(variable("On")),
            mangler.nameOf(variable("Light")),
        )

        assertThat(names).doesNotHaveDuplicates()
        assertThat(names).allMatch {
            identifier.matches(it)
        }
    }
}
