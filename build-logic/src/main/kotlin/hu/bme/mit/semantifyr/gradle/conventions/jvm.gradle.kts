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
    id("hu.bme.mit.semantifyr.gradle.conventions.pack")
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

val heavyCases = providers.gradleProperty("heavyCases").orElse(
    provider {
        gradle.startParameter.taskNames.any {
            it == "nightly" || it == ":nightly"
        }.toString()
    },
)

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
    testLogging.exceptionFormat = TestExceptionFormat.FULL
    workingDir = layout.projectDirectory.asFile
    // the JVM warnings the application convention silences
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("guice_bytecode_gen_option", "DISABLED")
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
    systemProperty("junit.jupiter.extensions.autodetection.include", "hu.bme.mit.semantifyr.*")
    systemProperty("semantifyr.heavyCases", heavyCases.get())
}

tasks.withType<Test>().matching {
    it.name != "test"
}.configureEach {
    the<JacocoTaskExtension>().isEnabled = false
}

// CI's test jobs start from the classes the Build job compiled
tasks.assemble {
    dependsOn(
        sourceSets.map {
            it.classesTaskName
        },
    )
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("guice_bytecode_gen_option", "DISABLED")
}

plugins.withId("org.jetbrains.kotlin.jvm") {
    apply(plugin = "org.jetbrains.dokka")
}

tasks.javadoc {
    // EMF and Xtext generate Javadoc that doclint rejects
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    destinationDir = layout.buildDirectory.dir("javadoc/${project.name}").get().asFile
}

configurations.consumable("javadocDirectoryElements") {
    attributes(semantifyrArtifact("javadoc"))
    outgoing.artifact(layout.buildDirectory.dir("javadoc")) {
        builtBy(tasks.javadoc)
    }
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
    inputs.files(tasks.jacocoTestReport)
}

// plain values only, as the plugin is not yet updated to providers
// FIXME: when the plugin is updated
sonar {
    properties {
        val nonMainSourceSets = sourceSets.filter {
            it.name != "main" && it.name != "testFixtures"
        }

        property("sonar.tests", nonMainSourceSets.flatMap {
            it.allSource.srcDirs
        }.filter {
            it.exists()
        })
        property("sonar.java.test.binaries", nonMainSourceSets.flatMap {
            it.output.classesDirs.files
        })
        property("sonar.coverage.jacoco.xmlReportPaths", tasks.withType<JacocoReport>().map {
            it.reports.xml.outputLocation.get().asFile
        }.filter {
            it.exists()
        })
    }
}
