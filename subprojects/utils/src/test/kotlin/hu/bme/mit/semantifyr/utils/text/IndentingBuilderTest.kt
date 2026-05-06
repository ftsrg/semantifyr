/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.text

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IndentingBuilderTest {

    @Test
    fun `single line has no leading indent`() {
        val builder = IndentingBuilder()
        builder.line("hello")
        assertThat(builder.toString()).isEqualTo("hello\n")
    }

    @Test
    fun `indented block prefixes inner lines with one indent`() {
        val builder = IndentingBuilder()
        builder.line("outer")
        builder.indented {
            line("inner")
        }
        builder.line("after")
        assertThat(builder.toString()).isEqualTo("outer\n    inner\nafter\n")
    }

    @Test
    fun `nested indented blocks stack indents`() {
        val builder = IndentingBuilder()
        builder.line("a")
        builder.indented {
            line("b")
            indented {
                line("c")
            }
            line("b2")
        }
        builder.line("d")
        assertThat(builder.toString()).isEqualTo(
            "a\n    b\n        c\n    b2\nd\n",
        )
    }

    @Test
    fun `empty line has no indent and just a newline`() {
        val builder = IndentingBuilder()
        builder.indented {
            line("x")
            line()
            line("y")
        }
        assertThat(builder.toString()).isEqualTo("    x\n\n    y\n")
    }

    @Test
    fun `indent level restored after a block that throws`() {
        val builder = IndentingBuilder()
        builder.line("before")
        runCatching {
            builder.indented {
                line("inside")
                error("boom")
            }
        }
        builder.line("after")
        assertThat(builder.toString()).isEqualTo("before\n    inside\nafter\n")
    }

    @Test
    fun `custom indent string is honoured`() {
        val builder = IndentingBuilder(indent = "\t")
        builder.line("a")
        builder.indented {
            line("b")
        }
        assertThat(builder.toString()).isEqualTo("a\n\tb\n")
    }

    @Test
    fun `lines writes multiple entries in order`() {
        val builder = IndentingBuilder()
        builder.indented {
            lines(listOf("one", "two", "three"))
        }
        assertThat(builder.toString()).isEqualTo("    one\n    two\n    three\n")
    }

    @Test
    fun `appendRaw writes the fragment verbatim without indent or newline`() {
        val builder = IndentingBuilder()
        builder.line("<open>")
        builder.appendRaw("   body with its own layout\n")
        builder.line("</open>")
        assertThat(builder.toString()).isEqualTo("<open>\n   body with its own layout\n</open>\n")
    }

    @Test
    fun `block emits header with brace, indented body, and closing brace`() {
        val builder = IndentingBuilder()
        builder.appendIndent("class X") {
            line("body")
        }
        assertThat(builder.toString()).isEqualTo("class X {\n    body\n}\n")
    }

    @Test
    fun `nested block stacks indent levels`() {
        val text = buildIndented("  ") {
            appendIndent("class X") {
                appendIndent("fun y") {
                    line("z")
                }
            }
        }
        assertThat(text).isEqualTo(
            """
            class X {
              fun y {
                z
              }
            }

            """.trimIndent(),
        )
    }

    @Test
    fun `buildIndented returns the produced string`() {
        val text = buildIndented {
            line("first")
            indented {
                line("second")
            }
        }
        assertThat(text).isEqualTo("first\n    second\n")
    }

    @Test
    fun `escapeXml translates the five standard entities`() {
        val input = "a<b>c&d\"e'f"
        assertThat(escapeXml(input)).isEqualTo("a&lt;b&gt;c&amp;d&quot;e&apos;f")
    }
}
