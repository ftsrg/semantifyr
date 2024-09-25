/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.engine.serialization

class IndentationAwareStringWriter(
    private val indentation: String
) {
    private val stringBuilder = StringBuilder()

    private var indentLevel = 0

    fun appendLine() {
        stringBuilder.appendLine()
    }

    fun append(string: String) {
        val indentedString = string.prependIndent(indentation.repeat(indentLevel))
        stringBuilder.append(indentedString)
    }

    fun appendLine(string: String) {
        val indentedString = string.prependIndent(indentation.repeat(indentLevel))
        stringBuilder.appendLine(indentedString)
    }

    inline fun indent(body: IndentationAwareStringWriter.() -> Unit) {
        indent()
        body()
        outdent()
    }

    fun indent() {
        indentLevel++
    }

    fun outdent() {
        indentLevel--
    }

    override fun toString() = stringBuilder.toString()

}

inline fun indent(indentation: String = "    ", body: IndentationAwareStringWriter.() -> Unit): String {
    val writer = IndentationAwareStringWriter(indentation)
    writer.body()
    return writer.toString()
}
