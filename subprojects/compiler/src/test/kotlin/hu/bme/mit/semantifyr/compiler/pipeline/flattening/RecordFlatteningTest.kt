/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.flattening.RecordFlatteningPass.Companion.FIELD_SEPARATOR
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceNames.INSTANCE_NAME_SEPARATOR
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecordFlatteningTest : FlatteningTestBase() {

    private val records = """
        package flattening::records
        enum Color { Red, Green }
        record Point {
            var x: int
            var y: int := 7
        }
        record Segment {
            var from: Point
            var to: Point
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

    @Test
    fun `a record variable becomes one scalar variable per field, nested records recursively`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var p: Point
                    var s: Segment
                    prop { return AG p.x == s.from.x }
                }
            """,
        )

        assertThat(flattened.inlinedOxsts.variables.map { it.name }).containsExactly(
            field("p", "x"),
            field("p", "y"),
            field("s", "from", "x"),
            field("s", "from", "y"),
            field("s", "to", "x"),
            field("s", "to", "y"),
            field("s", "color"),
        )
        assertThat(flattened.variableNamed(field("p", "x")).expression).isNull()
        assertThat(serialize(flattened.variableNamed(field("p", "y")))).contains(":= 7")
        assertThat(serialize(flattened.variableNamed(field("s", "color")))).contains("Color::Green")
        assertThat(serialize(flattened.inlinedOxsts.property)).contains("${quoted(field("p", "x"))} == ${quoted(field("s", "from", "x"))}")
    }

    @Test
    fun `field writes, record assignment, comparison and havoc work field by field`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var p: Point
                    var q: Point
                    redefine tran {
                        p.x := p.x + 1
                        q := p
                        havoc(p)
                    }
                    prop { return AG p != q }
                }
            """,
        )

        val px = quoted(field("p", "x"))
        val py = quoted(field("p", "y"))
        val qx = quoted(field("q", "x"))
        val qy = quoted(field("q", "y"))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).contains("$px := $px + 1", "$qx := $px", "$qy := $py", "havoc ($px)", "havoc ($py)")
        assertThat(serialize(flattened.inlinedOxsts.property)).contains("$px != $qx || $py != $qy")
    }

    @Test
    fun `record equality compares every leaf field`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var s: Segment
                    var t: Segment
                    prop { return AG s == t }
                }
            """,
        )

        val property = serialize(flattened.inlinedOxsts.property)
        for (path in listOf(listOf("from", "x"), listOf("from", "y"), listOf("to", "x"), listOf("to", "y"), listOf("color"))) {
            assertThat(property).contains("${quoted(field("s", *path.toTypedArray()))} == ${quoted(field("t", *path.toTypedArray()))}")
        }
    }

    @Test
    fun `a local record is declared field by field from its initializer`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var p: Point
                    redefine tran {
                        var copy: Point := p
                        copy.x := copy.x + 1
                        p := copy
                    }
                    prop { return AG p.x >= 0 }
                }
            """,
        )

        val px = Regex.escape(quoted(field("p", "x")))
        val py = Regex.escape(quoted(field("p", "y")))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).containsPattern("var ${localField("copy", "x")} : int := $px")
        assertThat(main).containsPattern("var ${localField("copy", "y")} : int := $py")
        assertThat(main).containsPattern("$px := ${localField("copy", "x")}")
    }

    @Test
    fun `an uninitialized local record takes the field defaults and havocs the rest`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var p: Point
                    redefine tran {
                        var fresh: Point
                        p := fresh
                    }
                    prop { return AG p.y == 7 }
                }
            """,
        )

        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).containsPattern("var ${localField("fresh", "x")} : int\\s+havoc \\(${localField("fresh", "x")}\\)")
        assertThat(main).containsPattern("var ${localField("fresh", "y")} : int := 7")
        assertThat(main).doesNotContainPattern("havoc \\(${localField("fresh", "y")}\\)")
    }

    @Test
    fun `record parameters and record-valued if-then-else are expanded field by field`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var p: Point
                    var q: Point
                    var flag: bool := false
                    tran move(target: Point) {
                        p := if flag then target else p
                    }
                    redefine tran { inline move(q) }
                    prop { return AG p == q }
                }
            """,
        )

        val flag = quoted(field("flag"))
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        for (name in listOf("x", "y")) {
            val p = quoted(field("p", name))
            val q = quoted(field("q", name))
            assertThat(main).contains("$p := if $flag then $q else $p")
        }
    }

    @Test
    fun `record fields back-annotate to their field path`() {
        val flattened = flatten(
            "Host",
            records + """
                @VerificationCase
                class Host {
                    var s: Segment
                    prop { return AG s.to.y == 7 }
                }
            """,
        )

        val leaf = flattened.variableNamed(field("s", "to", "y"))
        val recordField = flattened.flatteningInfo.recordFields.getValue(leaf)
        assertThat(recordField.recordVariable.name).isEqualTo("s")
        assertThat(recordField.fields.map { it.name }).containsExactly("to", "y")
        assertThat(flattened.flatteningInfo.variableHolders).containsKey(leaf)
    }
}
