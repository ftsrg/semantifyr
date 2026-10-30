/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.Task

// a task without outputs is never up to date, so checks that write no files get a stamp
fun Task.stampOnSuccess() {
    val stamp = project.layout.buildDirectory.file("$name.stamp")
    outputs.file(stamp)
    doLast {
        stamp.get().asFile.writeText("")
    }
}
