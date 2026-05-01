/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import java.io.File

fun prepareOutputFiles(
    logFile: File?,
    errorFile: File?,
    header: String? = null,
) {
    logFile?.ensureExists()
    logFile?.bufferedWriter()?.use {
        if (header != null) {
            it.appendLine(header)
        }
        it.appendLine()
    }

    errorFile?.ensureExists()
}

fun File.ensureExists(): File {
    parentFile?.mkdirs()
    createNewFile()
    return this
}
