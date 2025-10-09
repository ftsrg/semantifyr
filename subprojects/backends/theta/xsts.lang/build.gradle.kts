/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.xtext-generated")
}

dependencies {
    api(platform(libs.xtext.bom))
    api(libs.xtext.core)
    implementation(libs.xtext.xbase)

    testFixturesApi(libs.xtext.testing)

    mwe2(libs.xtext.generator)
    mwe2(libs.xtext.generator.antlr)
}

val generateXtextLanguage by tasks.registering(JavaExec::class) {
    mainClass.set("org.eclipse.emf.mwe2.launch.runtime.Mwe2Launcher")
    classpath(configurations.mwe2)

    inputs.file("src/main/java/hu/bme/mit/semantifyr/xsts/lang/GenerateXsts.mwe2")
    inputs.file("src/main/java/hu/bme/mit/semantifyr/xsts/lang/Xsts.xtext")

    outputs.dir("src/main/xtext-gen")
    outputs.dir("src/testFixtures/xtext-gen")
    outputs.dir(layout.buildDirectory.dir("generated/sources/xtext/ide"))

    args("src/main/java/hu/bme/mit/semantifyr/xsts/lang/GenerateXsts.mwe2", "-p", "rootPath=/$projectDir/..")
}

val ideGeneratedOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(ideGeneratedOutput.name, layout.buildDirectory.dir("generated/sources/xtext/ide")) {
        builtBy(generateXtextLanguage)
    }
}

tasks {
    jar {
        from(sourceSets.main.map { it.allSource }) {
            include("**/*.xtext")
        }
    }

    listOf("compileJava", "processResources", "compileTestJava", "compileTestFixturesJava", "processTestFixturesResources").forEach { task ->
        named(task) {
            inputs.files(generateXtextLanguage.get().outputs)
        }
    }

    clean {
        delete("src/main/xtext-gen")
        delete("src/test/java")
        delete("src/testFixtures/xtext-gen")
    }
}
