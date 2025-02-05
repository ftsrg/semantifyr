/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.xtext-generated")
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
}

val ideGeneratedClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    api(project(":gamma.lang"))

    implementation(libs.xtext.ide)
    runtimeOnly(libs.slf4j.log4j)

    ideGeneratedClasspath(project(":gamma.lang", configuration = "ideGeneratedOutput"))
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(distributionOutput.name, layout.buildDirectory.dir("install")) {
        builtBy(tasks.installDist)
    }
}

val cloneIdeGenerated by tasks.registering(Sync::class) {
    inputs.files(ideGeneratedClasspath)

    from(ideGeneratedClasspath.asFileTree)
    into("src/main/xtext-gen")
}

listOf("compileJava", "processResources").forEach { task ->
    tasks.named(task) {
        inputs.files(cloneIdeGenerated.get().outputs)
    }
}

tasks.clean {
    delete("src/main/xtext-gen")
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.lang.ide.GammaIdeSetup"
}
