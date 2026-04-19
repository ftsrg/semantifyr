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

    private fun domain(name: String): DomainDeclaration = mock<DomainDeclaration>().also {
        whenever(it.name).thenReturn(name)
    }

    private fun feature(name: String): FeatureDeclaration = mock<FeatureDeclaration>().also {
        whenever(it.name).thenReturn(name)
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
        val mid = Instance(domain("Mid"), parent = root, tree = tree)
        val leaf = Instance(domain("Leaf"), parent = mid, tree = tree)

        val sep = InstanceNames.INSTANCE_NAME_SEPARATOR
        assertThat(leaf.name).isEqualTo("${sep}Mid${sep}Leaf")
    }

    @Test
    fun `instancesAt on an empty slot returns an empty set`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)

        assertThat(root.instancesAt(feature("f"))).isEmpty()
    }

    @Test
    fun `placeInSlot makes the instance visible via instancesAt`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)
        val child = Instance(domain("Child"), parent = root, tree = tree)
        val f = feature("children")

        root.placeInSlot(f, child)

        assertThat(root.instancesAt(f)).containsExactly(child)
    }

    @Test
    fun `placeInSlot deduplicates the same instance added twice`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)
        val child = Instance(domain("Child"), parent = root, tree = tree)
        val f = feature("children")

        root.placeInSlot(f, child)
        root.placeInSlot(f, child)

        assertThat(root.instancesAt(f)).containsExactly(child)
    }

    @Test
    fun `children aggregates instances across all slots`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)
        val a = Instance(domain("A"), parent = root, tree = tree)
        val b = Instance(domain("B"), parent = root, tree = tree)
        val c = Instance(domain("C"), parent = root, tree = tree)
        val featureX = feature("x")
        val featureY = feature("y")

        root.placeInSlot(featureX, a)
        root.placeInSlot(featureX, b)
        root.placeInSlot(featureY, c)

        assertThat(root.children).containsExactlyInAnyOrder(a, b, c)
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
        val fx = feature("x")
        val fy = feature("y")

        root.placeInSlot(fx, child)

        assertThat(root.instancesAt(fx)).containsExactly(child)
        assertThat(root.instancesAt(fy)).isEmpty()
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
