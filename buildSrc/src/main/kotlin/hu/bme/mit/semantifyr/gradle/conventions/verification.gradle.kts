/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
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
    val testVerificationCases by tasks.registering(Test::class) {
        group = "verification"

        useJUnitPlatform {
            // we should use source sets instead
            excludeTags("benchmark")
            includeTags("verification")
        }

        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath

        usesService(verificationTestServiceProvider)

        // Stop on the first failing verification case so optimizer regressions
        // surface immediately instead of waiting through the full suite.
        failFast = true

        minHeapSize = "512m"
        maxHeapSize = "4G"
        testLogging.showStandardStreams = true
        testLogging.exceptionFormat = TestExceptionFormat.FULL

        maxParallelForks = 1

        shouldRunAfter(test)
        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        inputs.files(testVerificationCases.get().outputs)
        executionData(testVerificationCases.get())
    }

    check {
        inputs.files(testVerificationCases.get().outputs)
    }
}
