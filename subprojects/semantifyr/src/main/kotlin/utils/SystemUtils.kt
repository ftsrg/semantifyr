/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.utils

import java.io.File
import kotlin.reflect.KProperty

open class EnvVar {

    operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return System.getenv(property.name)
    }

}

@Suppress("ClassName")
object environment : EnvVar()

fun File.walkFiles() = walkTopDown().filter { it.isFile }
