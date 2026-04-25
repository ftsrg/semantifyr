/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.diffplug.spotless")
}

val libs = the<LibrariesForLibs>()

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    val googleJavaFormatVersion = libs.versions.googleJavaFormat.get()

    kotlin {
        target("src/**/*.kt")
        ktlint(ktlintVersion).editorConfigOverride(
            mapOf(
                "ktlint_standard_no-empty-file" to "disabled",
                "ij_kotlin_packages_to_use_import_on_demand" to "java.util.*,kotlinx.android.synthetic.**,io.ktor.**",
                "ktlint_function_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than" to "3",
            ),
        )
    }

    kotlinGradle {
        target("*.gradle.kts", "src/**/*.gradle.kts")
        ktlint(ktlintVersion)
    }

    java {
        target("src/**/*.java")
        targetExclude("**/xtext-gen/**", "**/emf-gen/**", "**/build/**")
        googleJavaFormat(googleJavaFormatVersion).aosp()
    }
}
