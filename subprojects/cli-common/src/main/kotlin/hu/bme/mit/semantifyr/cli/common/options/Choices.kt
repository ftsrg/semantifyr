/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common.options

import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.types.enum

private val WORD_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")

fun kebabCase(constant: Enum<*>): String {
    return constant.name.replace(WORD_BOUNDARY, "-").lowercase()
}

inline fun <reified T : Enum<T>> RawOption.kebabCaseEnum(): NullableOption<T, T> {
    return enum<T> {
        kebabCase(it)
    }
}
