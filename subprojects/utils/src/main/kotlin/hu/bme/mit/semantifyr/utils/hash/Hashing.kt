/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.hash

import java.io.Reader
import java.nio.file.Path
import java.security.MessageDigest

fun sha256Hex(reader: Reader): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = CharArray(8 * 1024)
    while (true) {
        val read = reader.read(buffer)
        if (read < 0) {
            break
        }
        digest.update(String(buffer, 0, read).toByteArray(Charsets.UTF_8))
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun sha256Hex(path: Path): String {
    path.toFile().bufferedReader().use {
        return sha256Hex(it)
    }
}
