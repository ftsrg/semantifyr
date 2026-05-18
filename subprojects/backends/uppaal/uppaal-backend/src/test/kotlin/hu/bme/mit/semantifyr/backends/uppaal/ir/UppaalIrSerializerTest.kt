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
                UppaalLocation(id = "c", name = "Committed", kind = UppaalLocationKind.Committed),
            ),
        )
        val xml = serializer.serialize(nta)
        assertThat(xml).contains("<committed/>")
    }

    @Test
    fun `urgent location emits urgent marker`() {
        val nta = minimalNtaWith(
            listOf(
                UppaalLocation(id = "u", name = "Urgent", kind = UppaalLocationKind.Urgent),
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
                        UppaalLocation("a", "A"),
                        UppaalLocation("b", "B"),
                    ),
                    initialLocationId = "a",
                    edges = listOf(
                        UppaalEdge(
                            sourceId = "a",
                            targetId = "b",
                            select = "i : int[0,3]",
                            guard = "x > 0",
                            assignment = "x = i",
                        ),
                    ),
                ),
            ),
        )
        val xml = serializer.serialize(nta)
        val selectIdx = xml.indexOf("kind=\"select\"")
        val guardIdx = xml.indexOf("kind=\"guard\"")
        val assignIdx = xml.indexOf("kind=\"assignment\"")
        assertThat(selectIdx).isGreaterThanOrEqualTo(0)
        assertThat(guardIdx).isGreaterThan(selectIdx)
        assertThat(assignIdx).isGreaterThan(guardIdx)
    }

    @Test
    fun `global declarations are emitted in a single declaration block with XML escaping`() {
        val nta = UppaalNta(
            globalDeclarations = UppaalDeclarations(
                typedefs = listOf("typedef int[0,2] Color;"),
                variables = listOf(
                    UppaalVariableDecl(typeName = "int", name = "x", initialValue = "0"),
                    UppaalVariableDecl(typeName = "bool", name = "b"),
                    UppaalVariableDecl(typeName = "clock", name = "c"),
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
        assertThat(xml).contains("int x = 0;")
        assertThat(xml).contains("bool b;")
        assertThat(xml).contains("clock c;")
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
                        UppaalLocation("a", "A"),
                        UppaalLocation("b", "B"),
                    ),
                    initialLocationId = "a",
                    edges = listOf(
                        UppaalEdge(sourceId = "a", targetId = "b", guard = "x < 5 && y > 0"),
                    ),
                ),
            ),
        )
        val xml = serializer.serialize(nta)
        assertThat(xml).contains("x &lt; 5 &amp;&amp; y &gt; 0")
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
