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

val thetaClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    thetaClasspath(project(":theta-wrapper", configuration = "thetaOutput"))
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
        inputs.files(thetaClasspath)

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

        val thetaCliPath = project(":theta-wrapper").layout.buildDirectory.dir("theta-xsts-cli").get().asFile.absolutePath
        val existingPath = environment["PATH"] ?: System.getenv("PATH") ?: ""
        environment("PATH", "$thetaCliPath${File.pathSeparator}$existingPath")

        shouldRunAfter(test)
        finalizedBy(jacocoTestReport)
    }

    val testSlowVerificationCases by tasks.registering(Test::class) {
        inputs.files(thetaClasspath)

        group = "verification"

        useJUnitPlatform {
//            includeTags("verification")
            includeTags("slow")
        }

        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath

        usesService(verificationTestServiceProvider)

        minHeapSize = "512m"
        maxHeapSize = "4G"
        testLogging.showStandardStreams = true
        testLogging.exceptionFormat = TestExceptionFormat.FULL

        val thetaCliPath = project(":theta-wrapper").layout.buildDirectory.dir("theta-xsts-cli").get().asFile.absolutePath
        val existingPath = environment["PATH"] ?: System.getenv("PATH") ?: ""
        environment("PATH", "$thetaCliPath${File.pathSeparator}$existingPath")

        maxParallelForks = 1
    }

    val allTests by tasks.registering(DefaultTask::class) {
        group = "verification"

        dependsOn(test)
        dependsOn(testVerificationCases)
        dependsOn(testSlowVerificationCases)
    }

    jacocoTestReport {
        inputs.files(testVerificationCases.get().outputs)
        executionData(testVerificationCases.get())
    }

    check {
        inputs.files(testVerificationCases.get().outputs)
    }
}
