/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.text

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IndentingStringBuilderTest {

    @Test
    fun `single line has no leading indent`() {
        val builder = IndentingStringBuilder()
        builder.appendLine("hello")
        assertThat(builder.toString()).isEqualTo("hello\n")
    }

    @Test
    fun `indented block prefixes inner lines with one indent`() {
        val builder = IndentingStringBuilder()
        builder.appendLine("outer")
        builder.indented {
            appendLine("inner")
        }
        builder.appendLine("after")
        assertThat(builder.toString()).isEqualTo("outer\n    inner\nafter\n")
    }

    @Test
    fun `nested indented blocks stack indents`() {
        val builder = IndentingStringBuilder()
        builder.appendLine("a")
        builder.indented {
            appendLine("b")
            indented {
                appendLine("c")
            }
            appendLine("b2")
        }
        builder.appendLine("d")
        assertThat(builder.toString()).isEqualTo(
            "a\n    b\n        c\n    b2\nd\n",
        )
    }

    @Test
    fun `empty line has no indent and just a newline`() {
        val builder = IndentingStringBuilder()
        builder.indented {
            appendLine("x")
            appendLine()
            appendLine("y")
        }
        assertThat(builder.toString()).isEqualTo("    x\n\n    y\n")
    }

    @Test
    fun `indent level restored after a block that throws`() {
        val builder = IndentingStringBuilder()
        builder.appendLine("before")
        runCatching {
            builder.indented {
                appendLine("inside")
                error("boom")
            }
        }
        builder.appendLine("after")
        assertThat(builder.toString()).isEqualTo("before\n    inside\nafter\n")
    }

    @Test
    fun `custom indent string is honoured`() {
        val builder = IndentingStringBuilder(indent = "\t")
        builder.appendLine("a")
        builder.indented {
            appendLine("b")
        }
        assertThat(builder.toString()).isEqualTo("a\n\tb\n")
    }

    @Test
    fun `appendRaw writes the fragment verbatim without indent or newline`() {
        val builder = IndentingStringBuilder()
        builder.appendLine("<open>")
        builder.appendRaw("   body with its own layout\n")
        builder.appendLine("</open>")
        assertThat(builder.toString()).isEqualTo("<open>\n   body with its own layout\n</open>\n")
    }

    @Test
    fun `block emits header with brace, indented body, and closing brace`() {
        val builder = IndentingStringBuilder()
        builder.appendIndent("class X") {
            appendLine("body")
        }
        assertThat(builder.toString()).isEqualTo("class X {\n    body\n}\n")
    }

    @Test
    fun `nested block stacks indent levels`() {
        val text = buildIndented("  ") {
            appendIndent("class X") {
                appendIndent("fun y") {
                    appendLine("z")
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
            appendLine("first")
            indented {
                appendLine("second")
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
