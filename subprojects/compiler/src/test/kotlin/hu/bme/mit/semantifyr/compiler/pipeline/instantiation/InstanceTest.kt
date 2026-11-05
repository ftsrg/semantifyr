/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.instantiation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InstanceTest {
    private val tree: InstanceTree = mock()

    private fun domain(name: String): DomainDeclaration {
        return mock<DomainDeclaration>().also {
            whenever(it.name).thenReturn(name)
        }
    }

    private fun feature(name: String): FeatureDeclaration {
        return mock<FeatureDeclaration>().also {
            whenever(it.name).thenReturn(name)
        }
    }

    @Test
    fun `root instance has the sentinel root name`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)

        assertThat(root.name).isEqualTo(InstanceNames.ROOT_INSTANCE_NAME)
    }

    @Test
    fun `child instance name is parent name, separator, then domain name`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)
        val child = Instance(domain("Leaf"), parent = root, tree = tree)

        assertThat(child.name).isEqualTo("${InstanceNames.ROOT_INSTANCE_NAME}${InstanceNames.INSTANCE_NAME_SEPARATOR}Leaf")
    }

    @Test
    fun `grandchild composes names recursively`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)
        val middle = Instance(domain("Middle"), parent = root, tree = tree)
        val leaf = Instance(domain("Leaf"), parent = middle, tree = tree)

        val separator = InstanceNames.INSTANCE_NAME_SEPARATOR
        assertThat(leaf.name).isEqualTo("${separator}Middle${separator}Leaf")
    }

    @Test
    fun `instancesAt on an empty slot returns an empty set`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)

        assertThat(root.instancesAt(feature("children"))).isEmpty()
    }

    @Test
    fun `placeInSlot makes the instance visible via instancesAt`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)
        val child = Instance(domain("Child"), parent = root, tree = tree)
        val childrenFeature = feature("children")

        root.placeInSlot(childrenFeature, child)

        assertThat(root.instancesAt(childrenFeature)).containsExactly(child)
    }

    @Test
    fun `placeInSlot deduplicates the same instance added twice`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)
        val child = Instance(domain("Child"), parent = root, tree = tree)
        val childrenFeature = feature("children")

        root.placeInSlot(childrenFeature, child)
        root.placeInSlot(childrenFeature, child)

        assertThat(root.instancesAt(childrenFeature)).containsExactly(child)
    }

    @Test
    fun `children aggregates instances across all slots`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)
        val firstSensor = Instance(domain("TemperatureSensor"), parent = root, tree = tree)
        val secondSensor = Instance(domain("PressureSensor"), parent = root, tree = tree)
        val valve = Instance(domain("Valve"), parent = root, tree = tree)
        val sensorsFeature = feature("sensors")
        val valvesFeature = feature("valves")

        root.placeInSlot(sensorsFeature, firstSensor)
        root.placeInSlot(sensorsFeature, secondSensor)
        root.placeInSlot(valvesFeature, valve)

        assertThat(root.children).containsExactlyInAnyOrder(firstSensor, secondSensor, valve)
    }

    @Test
    fun `children on a leaf is empty`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)

        assertThat(root.children).isEmpty()
    }

    @Test
    fun `instances placed in one slot are not returned by another`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)
        val child = Instance(domain("Child"), parent = root, tree = tree)
        val sensorsFeature = feature("sensors")
        val valvesFeature = feature("valves")

        root.placeInSlot(sensorsFeature, child)

        assertThat(root.instancesAt(sensorsFeature)).containsExactly(child)
        assertThat(root.instancesAt(valvesFeature)).isEmpty()
    }

    @Test
    fun `domain, parent, and tree fields are exposed as-is`() {
        val rootDomain = domain("Root")
        val childDomain = domain("Child")
        val root = Instance(rootDomain, parent = null, tree = tree)
        val child = Instance(childDomain, parent = root, tree = tree)

        assertThat(root.domain).isSameAs(rootDomain)
        assertThat(root.parent).isNull()
        assertThat(root.tree).isSameAs(tree)
        assertThat(child.parent).isSameAs(root)
        assertThat(child.domain).isSameAs(childDomain)
    }
}
