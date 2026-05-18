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
    val palantirVersion = libs.versions.palantirJavaFormat.get()

    kotlin {
        target(
            fileTree("src") {
                include("**/*.kt")
                exclude("**/xtext-gen/**", "**/emf-gen/**")
            },
        )
        ktlint(ktlintVersion).editorConfigOverride(
            mapOf(
                "ktlint_standard_no-empty-file" to "disabled",
                "ktlint_standard_chain-method-continuation" to "disabled",
                // "ktlint_standard_binary-expression-wrapping" to "disabled",
                "ktlint_standard_no-empty-first-line-at-start-in-class-body" to "disabled",
                "ktlint_standard_no-blank-line-before-rbrace" to "disabled",
                "ktlint_standard_unary-op-spacing" to "disabled",
                "ij_kotlin_packages_to_use_import_on_demand" to "java.util.*,kotlinx.android.synthetic.**,io.ktor.**",
                "ij_kotlin_line_break_after_multiline_when_entry" to "false",
                "ktlint_function_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than" to "3",
            ),
        )
    }

    kotlinGradle {
        target(
            fileTree(".") {
                include("*.gradle.kts", "src/**/*.gradle.kts")
                exclude("build/**", "**/xtext-gen/**", "**/emf-gen/**")
            },
        )
        ktlint(ktlintVersion)
    }

    java {
        target(
            fileTree("src") {
                include("**/*.java")
                exclude("**/xtext-gen/**", "**/emf-gen/**")
            },
        )
        palantirJavaFormat(palantirVersion)
    }
}
