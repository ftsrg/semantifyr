/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

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
    id("org.sonarqube")
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
        html.required = true
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
    inputs.files(tasks.test)
}

tasks.named("sonarResolver") {
    inputs.files(tasks.withType<JacocoReport>().map { it.outputs.files })
}

sonar {
    properties {
        val nonMainSourceSets = sourceSets.filter {
            it.name != "main" && it.name != "testFixtures"
        }

        val testSourceDirs = nonMainSourceSets.flatMap {
            it.allSource.srcDirs
        }.filter {
            it.exists()
        }

        val testBinaryDirs = nonMainSourceSets.flatMap {
            it.output.classesDirs.files
        }

        val testClasspath = nonMainSourceSets.flatMap {
            it.runtimeClasspath.files
        }.filter {
            it.exists()
        }

        val coverageXmls = tasks.withType<JacocoReport>().map {
            it.reports.xml.outputLocation.get().asFile
        }.filter {
            it.exists()
        }

        if (testSourceDirs.isNotEmpty()) {
            property("sonar.tests", testSourceDirs.joinToString(",") { it.absolutePath })
        }
        if (testBinaryDirs.isNotEmpty()) {
            property("sonar.java.test.binaries", testBinaryDirs.joinToString(",") { it.absolutePath })
        }
        if (testClasspath.isNotEmpty()) {
            property("sonar.java.test.libraries", testClasspath.joinToString(",") { it.absolutePath })
        }
        if (coverageXmls.isNotEmpty()) {
            property("sonar.coverage.jacoco.xmlReportPaths", coverageXmls.joinToString(",") { it.absolutePath })
        }
    }
}
