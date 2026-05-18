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
) {
    logFile?.ensureExists()
    errorFile?.ensureExists()
}

fun File.ensureExists(): File {
    parentFile?.mkdirs()
    createNewFile()
    return this
}
