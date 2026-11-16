/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.kotlin.dsl.of
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

abstract class TestChromium : ValueSource<String, TestChromium.Parameters> {
    interface Parameters : ValueSourceParameters {
        val chromium: Property<String>
        val purpose: Property<String>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val system = "${System.getProperty("os.name")} ${System.getProperty("os.arch")}"
        val chromium = parameters.chromium.orNull
        if (chromium.isNullOrEmpty()) {
            return "Playwright's Chromium on $system"
        }
        if (!File(chromium).exists()) {
            throw GradleException("${parameters.purpose.get()} Chromium, and SCREEN_TEST_CHROMIUM names $chromium, which does not exist. Name a Chromium with SCREEN_TEST_CHROMIUM=<path to its executable>, or leave it unset for Playwright's.")
        }
        val version = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(chromium, "--version")
            standardOutput = version
        }
        return "${version.toString().trim()} on $system"
    }
}

// what a browser draws and how it behaves follow its version and the system it runs on, so they key a browser task's cache beside Playwright's version in the lock file
fun Project.testChromium(purpose: String): Provider<String> {
    return providers.of(TestChromium::class) {
        parameters.chromium.set(providers.environmentVariable("SCREEN_TEST_CHROMIUM"))
        parameters.purpose.set(purpose)
    }
}
