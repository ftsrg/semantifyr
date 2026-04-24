/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.text

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
        sb.append('\n')
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

    fun appendRaw(text: String) {
        sb.append(text)
    }

    override fun toString(): String {
        return sb.toString()
    }
}

fun escapeXml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
