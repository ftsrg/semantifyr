/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.context

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlatteningInfoTest {

    private val holder: Instance = mock()
    private val otherHolder: Instance = mock()

    private fun variable(name: String): VariableDeclaration = mock<VariableDeclaration>().also {
        whenever(it.name).thenReturn(name)
    }

    @Test
    fun `resolveOriginalVariable maps actual back to original for the given holder`() {
        val original = variable("original")
        val actual = variable("holder_dollar_original")
        val info = FlatteningInfo(
            variableHolders = mapOf(actual to holder),
            variableInstanceDomains = emptyMap(),
            variableMappings = mapOf(holder to mapOf(original to actual)),
            instanceIdMapping = InstanceIdMapping(emptyMap(), emptyMap()),
        )

        assertThat(info.resolveOriginalVariable(holder, actual)).isSameAs(original)
    }

    @Test
    fun `resolveOriginalVariable throws for a holder with no mappings`() {
        val info = FlatteningInfo(
            variableHolders = emptyMap(),
            variableInstanceDomains = emptyMap(),
            variableMappings = emptyMap(),
            instanceIdMapping = InstanceIdMapping(emptyMap(), emptyMap()),
        )

        assertThatThrownBy {
            info.resolveOriginalVariable(holder, variable("x"))
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No variable mappings")
    }

    @Test
    fun `resolveOriginalVariable throws for a holder present in mappings but missing the actual variable`() {
        val original = variable("original")
        val actual = variable("actual")
        val missingActual = variable("missing")
        val info = FlatteningInfo(
            variableHolders = mapOf(actual to holder),
            variableInstanceDomains = emptyMap(),
            variableMappings = mapOf(holder to mapOf(original to actual)),
            instanceIdMapping = InstanceIdMapping(emptyMap(), emptyMap()),
        )

        assertThatThrownBy {
            info.resolveOriginalVariable(holder, missingActual)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("missing")
    }

    @Test
    fun `resolveOriginalVariable handles multiple holders independently`() {
        val originalA = variable("origA")
        val actualA = variable("actualA")
        val originalB = variable("origB")
        val actualB = variable("actualB")

        val info = FlatteningInfo(
            variableHolders = mapOf(actualA to holder, actualB to otherHolder),
            variableInstanceDomains = emptyMap(),
            variableMappings = mapOf(
                holder to mapOf(originalA to actualA),
                otherHolder to mapOf(originalB to actualB),
            ),
            instanceIdMapping = InstanceIdMapping(emptyMap(), emptyMap()),
        )

        assertThat(info.resolveOriginalVariable(holder, actualA)).isSameAs(originalA)
        assertThat(info.resolveOriginalVariable(otherHolder, actualB)).isSameAs(originalB)
    }

    @Test
    fun `resolveOriginalVariable throws when actual variable belongs to a different holder`() {
        val originalA = variable("origA")
        val actualA = variable("actualA")

        val info = FlatteningInfo(
            variableHolders = mapOf(actualA to holder),
            variableInstanceDomains = emptyMap(),
            variableMappings = mapOf(
                holder to mapOf(originalA to actualA),
                otherHolder to emptyMap(),
            ),
            instanceIdMapping = InstanceIdMapping(emptyMap(), emptyMap()),
        )

        assertThatThrownBy {
            info.resolveOriginalVariable(otherHolder, actualA)
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
