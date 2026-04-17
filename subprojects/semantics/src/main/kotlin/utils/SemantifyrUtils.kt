/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.utils

import hu.bme.mit.semantifyr.oxsts.lang.library.OxstsLibrary
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk

object SemantifyrUtils {

    fun modelPathsUnder(path: Path): Sequence<Path> {
        return path.walk(PathWalkOption.FOLLOW_LINKS).filter {
            it.isRegularFile() && it.name.endsWith(OxstsLibrary.FILE_NAME_SUFFIX)
        }
    }

}
