/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

class TestHomeArgumentProvider(
    @get:Internal val home: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        return listOf("-Duser.home=${home.absolutePath}")
    }
}

// the SysML translator caches its translations in the home directory, so each run starts from an empty home of its own and translates again
fun Test.translatesSysML() {
    val home = project.layout.buildDirectory.dir("test-home/$name").get().asFile
    jvmArgumentProviders.add(TestHomeArgumentProvider(home))
    doFirst {
        home.deleteRecursively()
    }
}
