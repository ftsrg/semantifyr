/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk

fun checkInputFile(
    kind: String,
    path: Path,
    fileSuffix: String,
    vararg otherFileSuffixes: String,
) {
    checkReadable(kind, path)
    val fileSuffixes = listOf(fileSuffix) + otherFileSuffixes
    val suffixed = fileSuffixes.any {
        path.name.endsWith(it)
    }
    if (path.isDirectory() || !suffixed) {
        throw CommandExit(ExitCode.Input, "$kind path '$path' is not a file ending in ${fileSuffixes.joinToString(" or ")}.")
    }
}

fun checkInputPath(
    kind: String,
    path: Path,
    fileExtensions: Collection<String>,
) {
    checkReadable(kind, path)
    val fileSuffixes = fileExtensions.map {
        ".$it"
    }
    val accepted = if (fileSuffixes.size == 1) {
        fileSuffixes.single()
    } else {
        "${fileSuffixes.dropLast(1).joinToString(", ")} or ${fileSuffixes.last()}"
    }
    if (!path.isDirectory()) {
        if (fileSuffixes.none { path.name.endsWith(it) }) {
            throw CommandExit(ExitCode.Input, "$kind path '$path' is neither a directory nor a file ending in $accepted.")
        }
        return
    }
    val files = try {
        path.walk(PathWalkOption.FOLLOW_LINKS).filter {
            it.isRegularFile() && fileSuffixes.any { suffix ->
                it.name.endsWith(suffix)
            }
        }.toList()
    } catch (exception: IOException) {
        throw CommandExit(ExitCode.Input, "$kind directory '$path' cannot be read: ${exception.message}")
    }
    if (files.isEmpty()) {
        throw CommandExit(ExitCode.Input, "$kind directory '$path' contains no $accepted file.")
    }
    val unreadable = files.firstOrNull {
        !it.isReadable()
    }
    if (unreadable != null) {
        throw CommandExit(ExitCode.Input, "$kind file '$unreadable' is not readable.")
    }
}

private fun checkReadable(
    kind: String,
    path: Path,
) {
    if (!path.exists()) {
        throw CommandExit(ExitCode.Input, "$kind path '$path' does not exist.")
    }
    if (!path.isReadable()) {
        throw CommandExit(ExitCode.Input, "$kind path '$path' is not readable.")
    }
}
