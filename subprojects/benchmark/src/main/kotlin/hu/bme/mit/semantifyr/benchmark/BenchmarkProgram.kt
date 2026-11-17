/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.asSequence

fun main(arguments: Array<String>) {
    val program = findClass("${arguments.first()}Kt") ?: findClass(arguments.first())
    requireNotNull(program) {
        "no program named ${arguments.first()} in the benchmark source set"
    }
    program.getMethod("main", Array<String>::class.java).invoke(null, arguments.drop(1).toTypedArray())
}

// the build's tasks name a class by its simple name, which only the benchmark source set's directories hold
internal fun findClass(simpleName: String): Class<*>? {
    val matches = System.getProperty("java.class.path").split(File.pathSeparator).map {
        Path(it)
    }.filter {
        it.isDirectory()
    }.flatMap { root ->
        Files.walk(root).use { paths ->
            paths.asSequence().filter {
                it.name == "$simpleName.class"
            }.map {
                root.relativize(it)
            }.toList()
        }
    }
    require(matches.size <= 1) {
        "several classes are named $simpleName: ${matches.joinToString()}"
    }
    return matches.singleOrNull()?.let {
        Class.forName(it.joinToString(".").removeSuffix(".class"))
    }
}
