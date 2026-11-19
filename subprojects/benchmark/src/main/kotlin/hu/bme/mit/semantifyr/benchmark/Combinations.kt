/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

fun <A, B, C> combinations(
    first: Map<String, A>,
    second: Map<String, B>,
    combine: (A, B) -> C,
): Map<String, C> {
    return first.entries.flatMap { (firstName, firstValue) ->
        second.map { (secondName, secondValue) ->
            "$firstName/$secondName" to combine(firstValue, secondValue)
        }
    }.toMap()
}
