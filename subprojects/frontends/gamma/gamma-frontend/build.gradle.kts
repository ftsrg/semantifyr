/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
    id("hu.bme.mit.semantifyr.gradle.conventions.integration")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":gamma.lang"))
    api(project(":verifier"))

    implementation(project(":logging"))
    implementation(project(":utils"))
    implementation(libs.kotlinx.coroutines.core)

    testFixturesApi(project(":gamma.lang"))
    testFixturesApi(testFixtures(project(":gamma.lang")))
}

val syncGammaLibrary by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("../models/libraries"))
    into(layout.buildDirectory.dir("libraries"))
}

val syncGammaTestModels by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("../models/examples"))
    into(layout.buildDirectory.dir("test-models"))
}

tasks.processResources {
    from(syncGammaLibrary)
}

tasks.withType<Test>().configureEach {
    inputs.files(syncGammaTestModels)
}

testing {
    suites {
        val verificationTest by getting(JvmTestSuite::class) {
            dependencies {
                implementation(project(":portfolios"))
            }
        }
    }
}

val compiledExamplesDir = layout.buildDirectory.dir("compiled-examples")
val examplesSourceDir = layout.projectDirectory.dir("../models/examples")

fun gammaExampleTask(name: String) = tasks.register<JavaExec>("compileGammaExample_$name") {
    val sourceFile = examplesSourceDir.file("$name.gamma")
    val targetFile = compiledExamplesDir.map { it.file("$name.oxsts") }
    inputs.file(sourceFile).withPathSensitivity(PathSensitivity.NAME_ONLY)
    inputs.files(sourceSets["main"].runtimeClasspath).withNormalizer(ClasspathNormalizer::class)
    outputs.file(targetFile)
    outputs.cacheIf { true }
    mainClass.set("hu.bme.mit.semantifyr.frontends.gamma.examples.CompileGammaExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    argumentProviders += CommandLineArgumentProvider {
        listOf(sourceFile.asFile.absolutePath, targetFile.get().asFile.absolutePath)
    }
}

val compileGammaExampleCrossroads = gammaExampleTask("Crossroads")
val compileGammaExampleSimple = gammaExampleTask("Simple")
val compileGammaExampleSpacecraft = gammaExampleTask("Spacecraft")

val compileGammaExamples by tasks.registering {
    inputs.files(compileGammaExampleCrossroads)
    inputs.files(compileGammaExampleSimple)
    inputs.files(compileGammaExampleSpacecraft)
}

val compiledExamples by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(compiledExamples.name, compiledExamplesDir) {
        builtBy(compileGammaExamples)
    }
}
