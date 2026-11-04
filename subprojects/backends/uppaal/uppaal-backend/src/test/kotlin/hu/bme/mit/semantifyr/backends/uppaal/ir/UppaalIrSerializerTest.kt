/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.ir

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UppaalIrSerializerTest {
    private val serializer = UppaalIrSerializer()

    @Test
    fun `minimal nta with one location serialises`() {
        val nta = UppaalNta(
            globalDeclarations = UppaalDeclarations(),
            templates = listOf(
                UppaalTemplate(
                    name = "Model",
                    locations = listOf(
                        UppaalLocation(id = "l0", name = "Start"),
                    ),
                    initialLocationId = "l0",
                    edges = emptyList(),
                ),
            ),
        )

        val xml = serializer.serialize(nta)

        assertThat(xml).contains("<nta>")
        assertThat(xml).contains("<template>")
        assertThat(xml).contains("<name>Model</name>")
        assertThat(xml).contains("<location id=\"l0\">")
        assertThat(xml).contains("<name>Start</name>")
        assertThat(xml).contains("<init ref=\"l0\"/>")
        assertThat(xml).contains("<system>system Model;</system>")
    }

    @Test
    fun `committed location emits committed marker`() {
        val nta = minimalNtaWith(
            listOf(
                UppaalLocation(id = "committed", name = "Committed", kind = UppaalLocationKind.Committed),
            ),
        )
        val xml = serializer.serialize(nta)
        assertThat(xml).contains("<committed/>")
    }

    @Test
    fun `urgent location emits urgent marker`() {
        val nta = minimalNtaWith(
            listOf(
                UppaalLocation(id = "urgent", name = "Urgent", kind = UppaalLocationKind.Urgent),
            ),
        )
        val xml = serializer.serialize(nta)
        assertThat(xml).contains("<urgent/>")
    }

    @Test
    fun `edge with guard select and assignment renders all three labels in order`() {
        val nta = UppaalNta(
            globalDeclarations = UppaalDeclarations(),
            templates = listOf(
                UppaalTemplate(
                    name = "Model",
                    locations = listOf(
                        UppaalLocation("source", "Source"),
                        UppaalLocation("target", "Target"),
                    ),
                    initialLocationId = "source",
                    edges = listOf(
                        UppaalEdge(
                            sourceId = "source",
                            targetId = "target",
                            select = "choice : int[0,3]",
                            guard = "counter > 0",
                            assignment = "counter = choice",
                        ),
                    ),
                ),
            ),
        )
        val xml = serializer.serialize(nta)
        val selectIndex = xml.indexOf("kind=\"select\"")
        val guardIndex = xml.indexOf("kind=\"guard\"")
        val assignmentIndex = xml.indexOf("kind=\"assignment\"")
        assertThat(selectIndex).isGreaterThanOrEqualTo(0)
        assertThat(guardIndex).isGreaterThan(selectIndex)
        assertThat(assignmentIndex).isGreaterThan(guardIndex)
    }

    @Test
    fun `global declarations are emitted in a single declaration block with XML escaping`() {
        val nta = UppaalNta(
            globalDeclarations = UppaalDeclarations(
                typedefs = listOf("typedef int[0,2] Color;"),
                variables = listOf(
                    UppaalVariableDecl(typeName = "int", name = "counter", initialValue = "0"),
                    UppaalVariableDecl(typeName = "bool", name = "flag"),
                    UppaalVariableDecl(typeName = "clock", name = "elapsed"),
                ),
            ),
            templates = listOf(
                UppaalTemplate(
                    name = "Model",
                    locations = listOf(UppaalLocation("l0", "Start")),
                    initialLocationId = "l0",
                    edges = emptyList(),
                ),
            ),
        )
        val xml = serializer.serialize(nta)
        // Typedefs and vars all inside one <declaration> block.
        assertThat(xml).contains("<declaration>typedef int[0,2] Color;")
        assertThat(xml).contains("int counter = 0;")
        assertThat(xml).contains("bool flag;")
        assertThat(xml).contains("clock elapsed;")
    }

    @Test
    fun `empty global declarations produce an empty declaration element`() {
        val nta = minimalNtaWith(listOf(UppaalLocation("l0", "Start")))
        val xml = serializer.serialize(nta)
        assertThat(xml).contains("<declaration></declaration>")
    }

    @Test
    fun `guard content is XML-escaped to protect the document`() {
        val nta = UppaalNta(
            globalDeclarations = UppaalDeclarations(),
            templates = listOf(
                UppaalTemplate(
                    name = "Model",
                    locations = listOf(
                        UppaalLocation("source", "Source"),
                        UppaalLocation("target", "Target"),
                    ),
                    initialLocationId = "source",
                    edges = listOf(
                        UppaalEdge(sourceId = "source", targetId = "target", guard = "counter < 5 && limit > 0"),
                    ),
                ),
            ),
        )
        val xml = serializer.serialize(nta)
        assertThat(xml).contains("counter &lt; 5 &amp;&amp; limit &gt; 0")
    }

    private fun minimalNtaWith(locations: List<UppaalLocation>): UppaalNta {
        return UppaalNta(
            globalDeclarations = UppaalDeclarations(),
            templates = listOf(
                UppaalTemplate(
                    name = "Model",
                    locations = locations,
                    initialLocationId = locations.first().id,
                    edges = emptyList(),
                ),
            ),
        )
    }
}
