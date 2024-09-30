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

dependencies {
    testFixturesApi(libs.junit.api)
    testFixturesApi(libs.junit.params)
    testFixturesApi(libs.mockito.core)
    testFixturesApi(libs.mockito.junit)

    testRuntimeOnly(libs.junit.engine)
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
    test {
        useJUnitPlatform {
            excludeTags("slow")
        }

        minHeapSize = "512m"
        maxHeapSize = "4G"
        testLogging.showStandardStreams = true
        testLogging.exceptionFormat = TestExceptionFormat.FULL

        finalizedBy(tasks.jacocoTestReport)
    }

    val allTests by tasks.creating(Test::class) {
        useJUnitPlatform()

        minHeapSize = "512m"
        maxHeapSize = "4G"
        testLogging.showStandardStreams = true

        finalizedBy(tasks.jacocoTestReport)
    }

    jacocoTestReport {
        inputs.files(test.get().outputs)
    }
}
