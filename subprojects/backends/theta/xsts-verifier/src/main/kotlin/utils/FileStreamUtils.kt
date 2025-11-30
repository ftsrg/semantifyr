/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.utils

import java.io.File
import java.io.OutputStream

fun File.ensureExistsOutputStream(): OutputStream {
    parentFile.mkdirs()
    createNewFile()
    return outputStream()
}
