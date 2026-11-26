/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.of
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

fun Project.testBrowser(): Provider<String> {
    return providers.gradleProperty("testBrowser").orElse("chromium")
}

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
            throw GradleException("${parameters.purpose.get()} Chromium, but SCREEN_TEST_CHROMIUM names a missing $chromium")
        }
        val version = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(chromium, "--version")
            standardOutput = version
        }
        return "${version.toString().trim()} on $system"
    }
}

fun Project.testChromium(purpose: String): Provider<String> {
    return providers.of(TestChromium::class) {
        parameters.chromium = providers.environmentVariable("SCREEN_TEST_CHROMIUM")
        parameters.purpose = purpose
    }
}
