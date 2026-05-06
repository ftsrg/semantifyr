/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.text

class IndentingBuilder(
    private val indent: String = "    ",
    private val sb: StringBuilder = StringBuilder(),
) {

    private var level: Int = 0

    fun line(text: String = "") {
        if (text.isNotEmpty()) {
            repeat(level) {
                sb.append(indent)
            }
            sb.append(text)
        }
        sb.append(System.lineSeparator())
    }

    fun lines(texts: Iterable<String>) {
        for (text in texts) {
            line(text)
        }
    }

    fun indented(block: IndentingBuilder.() -> Unit) {
        level++
        try {
            block()
        } finally {
            level--
        }
    }

    inline fun appendIndent(
        header: String,
        openSuffix: String = " {",
        closeChar: String = "}",
        crossinline body: IndentingBuilder.() -> Unit,
    ) {
        line(header + openSuffix)
        indented {
            body()
        }
        line(closeChar)
    }

    fun appendRaw(text: String) {
        sb.append(text)
    }

    override fun toString(): String {
        return sb.toString()
    }
}

inline fun buildIndented(
    indent: String = "    ",
    body: IndentingBuilder.() -> Unit,
): String {
    val builder = IndentingBuilder(indent)
    builder.body()
    return builder.toString()
}

fun escapeXml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
