/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.files

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

// only for content-addressed targets: a target placed by a concurrent writer holds the same content
fun moveIntoPlace(source: Path, target: Path) {
    try {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: IOException) {
        if (!Files.exists(target)) {
            throw e
        }
    }
}
