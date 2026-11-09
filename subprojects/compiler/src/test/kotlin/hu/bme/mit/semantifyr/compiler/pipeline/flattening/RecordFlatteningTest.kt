/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.flattening.RecordFlatteningPass.Companion.FIELD_SEPARATOR
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.LocalVarNames
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceNames.INSTANCE_NAME_SEPARATOR
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecordFlatteningTest : FlatteningTestBase() {

    private val records = """
        package flattening::records
        enum Color {
            Red,
            Green,
        }
        record Point {
            var x: int := 0
            var y: int := 7
        }
        record Segment {
            var from: Point := Point { x = 0 }
            var to: Point := Point { x = 0 }
            var color: Color := Color::Green
        }
    """

    private fun field(variable: String, vararg fields: String): String {
        return "$INSTANCE_NAME_SEPARATOR" + "root$INSTANCE_NAME_SEPARATOR$variable" + fields.joinToString("") {
            "$FIELD_SEPARATOR$it"
        }
    }

    private fun quoted(name: String): String {
        return "'$name'"
    }

    private fun localField(local: String, field: String): String {
        return "'${Regex.escape(local)}[^']*${Regex.escape("$FIELD_SEPARATOR$field")}'"
    }

    private fun saved(variable: String): String {
        return "'${Regex.escape(variable + LocalVarNames.LOCAL_VAR_SUFFIX)}\\d+'"
    }

    @Test
    fun `a record variable becomes one scalar variable per field, nested records recursively`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 2 }
                    var segment: Segment := Segment { color = Color::Green }
                    prop {
                        return AG position.x == segment.from.x
                    }
                }
            """,
        )

        assertThat(flattened.inlinedOxsts.variables.map { it.name }).containsExactly(
            field("position", "x"),
            field("position", "y"),
            field("segment", "from", "x"),
            field("segment", "from", "y"),
            field("segment", "to", "x"),
            field("segment", "to", "y"),
            field("segment", "color"),
        )
        assertThat(serialize(flattened.variableNamed(field("position", "x")))).contains(":= 2")
        assertThat(serialize(flattened.variableNamed(field("position", "y")))).contains(":= 7")
        assertThat(serialize(flattened.variableNamed(field("segment", "color")))).contains("Color::Green")
        assertThat(serialize(flattened.inlinedOxsts.property)).contains("${quoted(field("position", "x"))} == ${quoted(field("segment", "from", "x"))}")
    }

    @Test
    fun `field writes, record assignment, comparison and havoc work field by field`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 0 }
                    var previous: Point := Point { x = 0 }
                    redefine tran {
                        position.x := position.x + 1
                        previous := position
                        havoc(position)
                    }
                    prop {
                        return AG position != previous
                    }
                }
            """,
        )

        val positionX = quoted(field("position", "x"))
        val positionY = quoted(field("position", "y"))
        val previousX = quoted(field("previous", "x"))
        val previousY = quoted(field("previous", "y"))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).contains("$positionX := $positionX + 1", "$previousX := $positionX", "$previousY := $positionY", "havoc ($positionX)", "havoc ($positionY)")
        assertThat(serialize(flattened.inlinedOxsts.property)).contains("$positionX != $previousX || $positionY != $previousY")
    }

    @Test
    fun `record equality compares every leaf field`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var segment: Segment := Segment { color = Color::Green }
                    var otherSegment: Segment := Segment { color = Color::Green }
                    prop {
                        return AG segment == otherSegment
                    }
                }
            """,
        )

        val property = serialize(flattened.inlinedOxsts.property)
        for (path in listOf(listOf("from", "x"), listOf("from", "y"), listOf("to", "x"), listOf("to", "y"), listOf("color"))) {
            assertThat(property).contains("${quoted(field("segment", *path.toTypedArray()))} == ${quoted(field("otherSegment", *path.toTypedArray()))}")
        }
    }

    @Test
    fun `a local record is declared field by field from its initializer`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 0 }
                    redefine tran {
                        var copy: Point := position
                        copy.x := copy.x + 1
                        position := copy
                    }
                    prop {
                        return AG position.x >= 0
                    }
                }
            """,
        )

        val positionX = Regex.escape(quoted(field("position", "x")))
        val positionY = Regex.escape(quoted(field("position", "y")))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).containsPattern("var ${localField("copy", "x")} : int := $positionX")
        assertThat(main).containsPattern("var ${localField("copy", "y")} : int := $positionY")
        assertThat(main).containsPattern("$positionX := ${localField("copy", "x")}")
    }

    @Test
    fun `a local record takes its literal and the field defaults without a havoc`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 0 }
                    redefine tran {
                        var fresh: Point := Point { x = 4 }
                        position := fresh
                    }
                    prop {
                        return AG position.y == 7
                    }
                }
            """,
        )

        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).containsPattern("var ${localField("fresh", "x")} : int := 4")
        assertThat(main).containsPattern("var ${localField("fresh", "y")} : int := 7")
        assertThat(main).doesNotContain("havoc")
    }

    @Test
    fun `record parameters and record-valued if-then-else are expanded field by field`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 0 }
                    var previous: Point := Point { x = 0 }
                    var flag: bool := false
                    tran move(target: Point) {
                        position := if flag then target else position
                    }
                    redefine tran {
                        inline move(previous)
                    }
                    prop {
                        return AG position == previous
                    }
                }
            """,
        )

        val flag = quoted(field("flag"))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        for (name in listOf("x", "y")) {
            val position = quoted(field("position", name))
            val previous = quoted(field("previous", name))
            assertThat(main).contains("$position := if $flag then $previous else $position")
        }
    }

    @Test
    fun `record fields back-annotate to their field path`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var segment: Segment := Segment { color = Color::Green }
                    prop {
                        return AG segment.to.y == 7
                    }
                }
            """,
        )

        val leaf = flattened.variableNamed(field("segment", "to", "y"))
        val recordField = flattened.flatteningInfo.recordFields.getValue(leaf)
        assertThat(recordField.recordVariable.name).isEqualTo("segment")
        assertThat(recordField.fields.map { it.name }).containsExactly("to", "y")
        assertThat(flattened.flatteningInfo.variableHolders).containsKey(leaf)
    }

    @Test
    fun `a record literal is assigned field by field and an omitted field takes its default`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 0 }
                    var offset: int := 0
                    redefine tran {
                        position := Point { x = offset + 1 }
                    }
                    prop {
                        return AG position.x >= 0
                    }
                }
            """,
        )

        val positionX = quoted(field("position", "x"))
        val positionY = quoted(field("position", "y"))
        val offset = quoted(field("offset"))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).contains("$positionX := $offset + 1", "$positionY := 7")
        assertThat(main).doesNotContain("Point")
    }

    @Test
    fun `a record literal initializes a class variable and a local variable field by field`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 1, y = 2 }
                    redefine tran {
                        var local: Point := Point { x = position.y }
                        position := local
                    }
                    prop {
                        return AG position.x >= 0
                    }
                }
            """,
        )

        assertThat(serialize(flattened.variableNamed(field("position", "x")))).contains(":= 1")
        assertThat(serialize(flattened.variableNamed(field("position", "y")))).contains(":= 2")
        val positionY = Regex.escape(quoted(field("position", "y")))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).containsPattern("var ${localField("local", "x")} : int := $positionY")
        assertThat(main).containsPattern("var ${localField("local", "y")} : int := 7")
    }

    @Test
    fun `comparison with a record literal compares every field`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 0 }
                    var segment: Segment := Segment { color = Color::Green }
                    prop {
                        return AG (position != Point { x = 1, y = 2 } || segment == Segment { from = position, to = Point { x = 3 } })
                    }
                }
            """,
        )

        val positionX = quoted(field("position", "x"))
        val positionY = quoted(field("position", "y"))
        val property = serialize(flattened.inlinedOxsts.property)
        assertThat(property).contains("$positionX != 1 || $positionY != 2")
        assertThat(property).contains(
            "${quoted(field("segment", "from", "x"))} == $positionX",
            "${quoted(field("segment", "from", "y"))} == $positionY",
            "${quoted(field("segment", "to", "x"))} == 3",
            "${quoted(field("segment", "to", "y"))} == 7",
            "${quoted(field("segment", "color"))} == ",
        )
    }

    @Test
    fun `a record-typed field takes its literal default and nested literals flatten to the leaves`() {
        val flattened = flatten(
            "Host",
            records + """
                record Line {
                    var from: Point := Point { x = 1, y = 1 }
                    var to: Point := Point { x = 2 }
                }
                @VerificationCase
                class Host {
                    var defaultLine: Line := Line { from = Point { x = 1, y = 1 } }
                    var initializedLine: Line := Line { to = Point { x = 3 } }
                    prop {
                        return AG defaultLine.from.x == initializedLine.to.x
                    }
                }
            """,
        )

        assertThat(serialize(flattened.variableNamed(field("defaultLine", "from", "x")))).contains(":= 1")
        assertThat(serialize(flattened.variableNamed(field("defaultLine", "from", "y")))).contains(":= 1")
        assertThat(serialize(flattened.variableNamed(field("defaultLine", "to", "x")))).contains(":= 2")
        assertThat(serialize(flattened.variableNamed(field("defaultLine", "to", "y")))).contains(":= 7")
        assertThat(serialize(flattened.variableNamed(field("initializedLine", "from", "x")))).contains(":= 1")
        assertThat(serialize(flattened.variableNamed(field("initializedLine", "to", "x")))).contains(":= 3")
        assertThat(serialize(flattened.variableNamed(field("initializedLine", "to", "y")))).contains(":= 7")
    }

    @Test
    fun `a record literal argument is passed by value and its fields are read directly`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 0 }
                    var last: int := 0
                    tran moveTo(target: Point) {
                        position := target
                        last := target.x
                    }
                    redefine tran {
                        inline moveTo(Point { x = last + 1 })
                    }
                    prop {
                        return AG position.x == last
                    }
                }
            """,
        )

        val positionX = quoted(field("position", "x"))
        val positionY = quoted(field("position", "y"))
        val last = quoted(field("last"))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).contains("$positionX := $last + 1", "$positionY := 7", "$last := $last + 1")
    }

    @Test
    fun `a record literal in an if-then-else is split with the other branch`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 0 }
                    var flag: bool := false
                    redefine tran {
                        position := if flag then Point { x = 1 } else position
                    }
                    prop {
                        return AG position.x >= 0
                    }
                }
            """,
        )

        val positionX = quoted(field("position", "x"))
        val positionY = quoted(field("position", "y"))
        val flag = quoted(field("flag"))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).contains("$positionX := if $flag then 1 else $positionX", "$positionY := if $flag then 7 else $positionY")
    }

    @Test
    fun `a call inside a record literal is inlined`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 0 }
                    prop next(): int {
                        return position.x + 1
                    }
                    redefine tran {
                        position := Point { x = next(), y = Point { x = next() }.x }
                    }
                    prop {
                        return AG position.x >= 0
                    }
                }
            """,
        )

        val positionX = Regex.escape(quoted(field("position", "x")))
        val positionY = Regex.escape(quoted(field("position", "y")))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).containsPattern("var ${saved(field("position", "y"))} : int := $positionX \\+ 1\\s+$positionX := $positionX \\+ 1\\s+$positionY := ${saved(field("position", "y"))}")
    }

    @Test
    fun `a record value that reads a field written before it is saved first`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var position: Point := Point { x = 0 }
                    var previous: Point := Point { x = 0 }
                    redefine tran {
                        position := Point { x = position.y, y = position.x }
                        previous := if previous.x > 0 then position else previous
                    }
                    prop {
                        return AG position.x >= 0
                    }
                }
            """,
        )

        val positionX = Regex.escape(quoted(field("position", "x")))
        val positionY = Regex.escape(quoted(field("position", "y")))
        val previousX = Regex.escape(quoted(field("previous", "x")))
        val previousY = Regex.escape(quoted(field("previous", "y")))
        val savedPy = saved(field("position", "y"))
        val savedGuard = saved(field("previous", "y"))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).containsPattern("var $savedPy : int := $positionX\\s+$positionX := $positionY\\s+$positionY := $savedPy")
        assertThat(main).containsPattern("var $savedGuard : int := if $previousX > 0 then $positionY else $previousY\\s+$previousX := if $previousX > 0 then $positionX else $previousX\\s+$previousY := $savedGuard")
    }
}
