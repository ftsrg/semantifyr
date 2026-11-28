/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

fun Test.readsTestModels(vararg folders: String) {
    val testModels = project.isolated.rootProject.projectDirectory.dir("oxsts-test-models")
    if (folders.isEmpty()) {
        inputs.dir(testModels).withPathSensitivity(PathSensitivity.RELATIVE)
    }
    folders.forEach {
        inputs.dir(testModels.dir(it)).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
