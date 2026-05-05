/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.gamma.serialization.IndentationAwareStringWriter
import hu.bme.mit.semantifyr.frontends.gamma.serialization.appendIndent
import hu.bme.mit.semantifyr.frontends.gamma.serialization.indent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IndentationAwareStringWriterTests {

    @Test
    fun `appendLine writes a line with no indent at the top level`() {
        val text = indent {
            appendLine("hello")
        }

        assertThat(text).isEqualTo("hello\n")
    }

    @Test
    fun `nested indent prepends the indent string per level`() {
        val text = indent("  ") {
            appendLine("outer")
            indent {
                appendLine("inner")
                indent {
                    appendLine("deepest")
                }
            }
        }

        assertThat(text).isEqualTo(
            """
            outer
              inner
                deepest

            """.trimIndent(),
        )
    }

    @Test
    fun `appendIndent emits an opening brace, indented body, and closing brace`() {
        val text = indent("  ") {
            appendIndent("class X") {
                appendLine("body")
            }
        }

        assertThat(text).isEqualTo(
            """
            class X {
              body
            }

            """.trimIndent(),
        )
    }

    @Test
    fun `appendLine with no argument writes a bare newline at any indent level`() {
        val writer = IndentationAwareStringWriter("  ")
        writer.indent()
        writer.indent()
        writer.appendLine("body")
        writer.appendLine()
        writer.appendLine("more body")

        assertThat(writer.toString()).isEqualTo("    body\n\n    more body\n")
    }
}
