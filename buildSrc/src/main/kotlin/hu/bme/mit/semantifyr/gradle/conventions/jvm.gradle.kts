/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.process.CommandLineArgumentProvider

class MockitoAgentArgumentProvider(
    @get:Classpath val agent: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        return listOf("-javaagent:${agent.asPath}", "-Xshare:off")
    }
}

plugins {
    `java-library`
    `java-test-fixtures`
    jacoco
    id("hu.bme.mit.semantifyr.gradle.conventions.formatting")
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
    testLogging.exceptionFormat = TestExceptionFormat.FULL
    workingDir = layout.projectDirectory.asFile
}

tasks.withType<JacocoReport>().configureEach {
    group = "verification"
    sourceSets(sourceSets.main.get())
    reports {
        xml.required = true
    }
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
                    jvmArgumentProviders.add(MockitoAgentArgumentProvider(mockitoAgent))
                    finalizedBy(tasks.jacocoTestReport)
                }
            }
        }
    }
}

tasks.jacocoTestReport {
    inputs.files(tasks.test.get().outputs)
}
