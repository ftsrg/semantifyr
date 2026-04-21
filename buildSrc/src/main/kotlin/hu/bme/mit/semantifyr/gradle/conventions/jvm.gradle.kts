/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
    `java-test-fixtures`
    jacoco
}

repositories {
    mavenCentral()
}

val libs = the<LibrariesForLibs>()

val mockitoAgent by configurations.creating

dependencies {
    testFixturesApi(libs.bundles.junit.fixtures)
    testFixturesApi(libs.assertj.core)
    testFixturesApi(libs.bundles.mockito)

    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

java.toolchain {
    languageVersion = JavaLanguageVersion.of(25)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                runtimeOnly.bundle(libs.bundles.junit.runtime)
                runtimeOnly(libs.slf4j.log4j)
            }

            targets.all {
                testTask.configure {
                    jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
                    testLogging.showStandardStreams = true
                    testLogging.exceptionFormat = TestExceptionFormat.FULL
                    finalizedBy(tasks.jacocoTestReport)
                }
            }
        }
    }
}
