/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.context

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class InstanceIdMappingTest {

    private val instance1: Instance = mock()
    private val instance2: Instance = mock()

    @Test
    fun `instanceOfId returns the mapped instance`() {
        val mapping = InstanceIdMapping(
            instanceToId = mapOf(instance1 to 1, instance2 to 2),
            idToInstance = mapOf(1 to instance1, 2 to instance2),
        )

        assertThat(mapping.instanceOfId(1)).isSameAs(instance1)
        assertThat(mapping.instanceOfId(2)).isSameAs(instance2)
    }

    @Test
    fun `instanceOfId throws for an unknown id`() {
        val mapping = InstanceIdMapping(emptyMap(), emptyMap())

        assertThatThrownBy { mapping.instanceOfId(42) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("42")
    }

    @Test
    fun `entries exposes the full instance-to-id mapping`() {
        val mapping = InstanceIdMapping(
            instanceToId = mapOf(instance1 to 1, instance2 to 2),
            idToInstance = mapOf(1 to instance1, 2 to instance2),
        )

        val pairs = mapping.entries.map { it.key to it.value }
        assertThat(pairs).containsExactlyInAnyOrder(instance1 to 1, instance2 to 2)
    }

    @Test
    fun `entries on empty mapping is empty`() {
        val mapping = InstanceIdMapping(emptyMap(), emptyMap())

        assertThat(mapping.entries).isEmpty()
    }
}
