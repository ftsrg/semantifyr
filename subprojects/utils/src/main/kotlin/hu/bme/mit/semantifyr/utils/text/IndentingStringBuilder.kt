/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.text

class IndentingStringBuilder(
    private val indent: String = "    ",
    private val sb: StringBuilder = StringBuilder(),
) {

    private var level: Int = 0

    fun appendLine(text: String = "") {
        if (text.isNotEmpty()) {
            repeat(level) {
                sb.append(indent)
            }
            sb.append(text)
        }
        sb.append(System.lineSeparator())
    }

    fun indented(block: IndentingStringBuilder.() -> Unit) {
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
        crossinline body: IndentingStringBuilder.() -> Unit,
    ) {
        appendLine(header + openSuffix)
        indented {
            body()
        }
        appendLine(closeChar)
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
    body: IndentingStringBuilder.() -> Unit,
): String {
    val builder = IndentingStringBuilder(indent)
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
