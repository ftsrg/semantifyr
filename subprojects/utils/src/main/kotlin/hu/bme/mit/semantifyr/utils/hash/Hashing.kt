/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.hash

import java.io.DataOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.HexFormat

fun sha256Hex(files: Map<String, ByteArray>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    DataOutputStream(DigestOutputStream(OutputStream.nullOutputStream(), digest)).use {
        for ((name, content) in files.toSortedMap()) {
            it.writeUTF(name)
            it.writeInt(content.size)
            it.write(content)
        }
    }
    return HexFormat.of().formatHex(digest.digest())
}
