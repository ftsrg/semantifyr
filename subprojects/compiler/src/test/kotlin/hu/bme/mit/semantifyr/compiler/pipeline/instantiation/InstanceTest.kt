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
    fun `an instance is named by its ancestors' domains below the root`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)
        val middle = Instance(domain("Middle"), parent = root, tree = tree)
        val leaf = Instance(domain("Leaf"), parent = middle, tree = tree)

        val separator = InstanceNames.INSTANCE_NAME_SEPARATOR
        assertThat(root.name).isEqualTo(InstanceNames.ROOT_INSTANCE_NAME)
        assertThat(middle.name).isEqualTo("${InstanceNames.ROOT_INSTANCE_NAME}${separator}Middle")
        assertThat(leaf.name).isEqualTo("${separator}Middle${separator}Leaf")
    }

    @Test
    fun `a slot holds each instance placed in it once and nothing placed in another slot`() {
        val root = Instance(domain("Root"), parent = null, tree = tree)
        val child = Instance(domain("Child"), parent = root, tree = tree)
        val sensorsFeature = feature("sensors")
        val valvesFeature = feature("valves")

        root.placeInSlot(sensorsFeature, child)
        root.placeInSlot(sensorsFeature, child)

        assertThat(root.instancesAt(sensorsFeature)).containsExactly(child)
        assertThat(root.instancesAt(valvesFeature)).isEmpty()
    }

    @Test
    fun `the children are the instances of every slot`() {
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
        assertThat(valve.children).isEmpty()
    }
}
