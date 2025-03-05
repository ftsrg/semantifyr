/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    alias(libs.plugins.kotlin.jvm)
}

tasks.withType(Test::class.java) {
    inputs.dir("TestModels")

    if (environment["thetaVersion"] == null) {
        environment("thetaVersion", findProperty("thetaVersion")!!)
    }
}

val GPR_USER: String by project
val GPR_KEY: String by project

repositories {
    mavenCentral()
    maven("https://repo.eclipse.org/content/groups/viatra/")
    maven {
        url = uri("https://maven.pkg.github.com/Systems-Modeling/SysML-v2-Pilot-Implementation")
        credentials {
            username = System.getenv("GPR_USER") ?: GPR_USER
            password = System.getenv("GPR_KEY") ?: GPR_KEY
        }
    }
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

dependencies {
    implementation(libs.sysml.kerml.expressions)
    implementation(libs.sysml.kerml)
    implementation(libs.sysml.sysml.xtext)

    api(libs.sysml.sysml)

    implementation(project(":semantifyr"))

    implementation(platform(libs.xtext.bom))
    implementation(libs.xtext.core)

    implementation(libs.guice)
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.clikt)
    implementation(libs.ecore)
    implementation(libs.ecore)

    runtimeOnly(libs.slf4j.log4j)

    testFixturesApi(project(":gamma.lang"))
    testFixturesApi(testFixtures(project(":gamma.lang")))
    testFixturesApi(project(":semantifyr"))
    testFixturesApi(testFixtures(project(":semantifyr")))
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.frontend.GammaFrontendCliKt"
}
