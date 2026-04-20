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
    java
}

repositories {
    mavenCentral()
}

val libs = the<LibrariesForLibs>()

val mockitoAgent by configurations.creating

dependencies {
    testFixturesApi(libs.junit.api)
    testFixturesApi(libs.assertj.core)
    testFixturesApi(libs.junit.params)
    testFixturesApi(libs.mockito.core)
    testFixturesApi(libs.mockito.junit)
    testFixturesApi(libs.mockito.kotlin)

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
                runtimeOnly(libs.junit.engine)
                runtimeOnly(libs.junit.platform.launcher)
                runtimeOnly(libs.slf4j.log4j)
            }

            targets.all {
                testTask.configure {
                    jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
                    minHeapSize = "512m"
                    maxHeapSize = "4G"
                    testLogging.showStandardStreams = true
                    testLogging.exceptionFormat = TestExceptionFormat.FULL
                    finalizedBy(tasks.jacocoTestReport)
                }
            }
        }
    }
}
