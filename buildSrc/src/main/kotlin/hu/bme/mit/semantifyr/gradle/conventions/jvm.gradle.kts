/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.registering

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
    testFixturesApi(libs.assertj.core)
    testFixturesApi(libs.junit.params)
    testFixturesApi(libs.mockito.core)
    testFixturesApi(libs.mockito.junit)

    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java.toolchain {
    languageVersion = JavaLanguageVersion.of(25)
}

/*
 * Simple Build service that acts as a lock or throttling device preventing parallel execution
 */
abstract class VerificationTestService : BuildService<BuildServiceParameters.None>
val verificationTestServiceName = "verificationTestService"
val verificationTestServiceProvider = gradle.sharedServices.registerIfAbsent(verificationTestServiceName, VerificationTestService::class) {
    // only allow at most 1 verification task per Gradle build
    maxParallelUsages.set(1)
}

tasks {
    test {
        useJUnitPlatform {
            excludeTags("verification")
            excludeTags("slow")
        }

        minHeapSize = "512m"
        maxHeapSize = "4G"
        testLogging.showStandardStreams = true
        testLogging.exceptionFormat = TestExceptionFormat.FULL

        finalizedBy(tasks.jacocoTestReport)
    }

    val verificationTest by tasks.registering(Test::class) {
        group = "verification"

        useJUnitPlatform {
            includeTags("verification")
            excludeTags("slow")
        }

        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath

        usesService(verificationTestServiceProvider)

        minHeapSize = "512m"
        maxHeapSize = "4G"
        testLogging.showStandardStreams = true
        testLogging.exceptionFormat = TestExceptionFormat.FULL

        maxParallelForks = 1

        shouldRunAfter(test)
        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        inputs.files(test.get().outputs)
        inputs.files(verificationTest.get().outputs)

        executionData(test.get(), verificationTest.get())
    }

    check {
        inputs.files(verificationTest.get().outputs)
    }
}
