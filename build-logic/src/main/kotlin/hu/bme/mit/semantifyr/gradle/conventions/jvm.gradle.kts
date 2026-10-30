/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
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

val libs = the<LibrariesForLibs>()

val mockitoAgent = configurations.dependencyScope("mockitoAgent")
val mockitoAgentClasspath = configurations.resolvable("mockitoAgentClasspath") {
    extendsFrom(mockitoAgent.get())
    isTransitive = false
    attributes(javaRuntimeLibrary())
}

dependencies {
    testFixturesApi(libs.bundles.junit.fixtures)
    testFixturesApi(libs.assertj.core)
    testFixturesApi(libs.bundles.mockito)

    mockitoAgent(libs.mockito.core)
}

java.toolchain {
    languageVersion = JavaLanguageVersion.of(25)
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
    testLogging.exceptionFormat = TestExceptionFormat.FULL
    workingDir = layout.projectDirectory.asFile
    systemProperty("semantifyr.tests.requiredBackends", providers.gradleProperty("tests.requiredBackends").getOrElse(""))
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
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)

            dependencies {
                runtimeOnly(libs.slf4j.log4j)
            }

            targets.all {
                testTask.configure {
                    jvmArgumentProviders.add(MockitoAgentArgumentProvider(files(mockitoAgentClasspath)))
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
    // the nightly runs only on fured, so its report must not pull nightlyTest into CI
    inputs.files(tasks.withType<JacocoReport>().matching { it.name != "jacocoNightlyTestReport" })
}

sonar {
    properties {
        val nonMainSourceSets = sourceSets.filter {
            it.name != "main" && it.name != "testFixtures"
        }

        property("sonar.tests", provider {
            nonMainSourceSets.flatMap {
                it.allSource.srcDirs
            }.filter {
                it.exists()
            }.joinToString(",") {
                it.absolutePath
            }
        })
        property("sonar.java.test.binaries", provider {
            nonMainSourceSets.flatMap {
                it.output.classesDirs.files
            }.joinToString(",") {
                it.absolutePath
            }
        })
        property("sonar.java.test.libraries", provider {
            nonMainSourceSets.flatMap {
                it.runtimeClasspath.files
            }.filter {
                it.exists()
            }.joinToString(",") {
                it.absolutePath
            }
        })
        property("sonar.coverage.jacoco.xmlReportPaths", provider {
            tasks.withType<JacocoReport>().map {
                it.reports.xml.outputLocation.get().asFile
            }.filter {
                it.exists()
            }.joinToString(",") {
                it.absolutePath
            }
        })
    }
}
