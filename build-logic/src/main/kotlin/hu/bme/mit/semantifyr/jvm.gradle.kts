/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import hu.bme.mit.semantifyr.gradle.javaRuntimeLibrary
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

class MockitoAgentArgumentProvider(
    @get:Classpath val agent: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        return listOf(
            "-javaagent:${agent.asPath}",
            "-Xshare:off",
        )
    }
}

plugins {
    `java-library`
    `java-test-fixtures`
    jacoco
    id("hu.bme.mit.semantifyr.formatting")
    id("org.sonarqube")
    id("org.jetbrains.dokka")
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
    testLogging.showStandardStreams = true
    testLogging.exceptionFormat = TestExceptionFormat.FULL
    // the JVM warnings the cli convention silences
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("guice_bytecode_gen_option", "DISABLED")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("guice_bytecode_gen_option", "DISABLED")
}

testing {
    suites {
        withType<JvmTestSuite>().configureEach {
            useJUnitJupiter(libs.versions.junit)

            dependencies {
                runtimeOnly(libs.slf4j.log4j)
            }
        }

        named<JvmTestSuite>("test") {
            targets.all {
                testTask.configure {
                    jvmArgumentProviders.add(MockitoAgentArgumentProvider(files(mockitoAgentClasspath)))
                }
            }
        }
    }
}

// plain values only, as the plugin is not yet updated to providers
// FIXME: when the plugin is updated
sonar {
    properties {
        val testSourceSets = testing.suites.withType<JvmTestSuite>().map {
            it.sources
        }

        property("sonar.tests", testSourceSets.flatMap {
            it.allSource.srcDirs
        }.filter {
            it.exists()
        })
        property("sonar.java.test.binaries", testSourceSets.flatMap {
            it.output.classesDirs.files
        })
    }
}
