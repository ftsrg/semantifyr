/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

fun Any.testModel(name: String): String {
    return checkNotNull(javaClass.getResource("${javaClass.simpleName}/$name")) {
        "${javaClass.simpleName} has no test model $name"
    }.readText()
}
