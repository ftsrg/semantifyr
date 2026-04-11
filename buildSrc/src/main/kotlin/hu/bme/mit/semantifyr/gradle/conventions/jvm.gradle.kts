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

    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

java.toolchain {
    languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    // TODO: refactor tests to use test suites
    test {
        jvmArgs.add("-javaagent:${mockitoAgent.asPath}")

        useJUnitPlatform {
            // we should use source sets instead
            excludeTags("benchmark")
            excludeTags("verification")
            excludeTags("slow")
        }

        minHeapSize = "512m"
        maxHeapSize = "4G"
        testLogging.showStandardStreams = true
        testLogging.exceptionFormat = TestExceptionFormat.FULL

        finalizedBy(tasks.jacocoTestReport)
    }

    jacocoTestReport {
        inputs.files(test.get().outputs)
        executionData(test.get())
    }
}
