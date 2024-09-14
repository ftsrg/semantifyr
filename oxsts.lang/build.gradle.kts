/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.xtext-generated")
    id("hu.bme.mit.semantifyr.gradle.eclipse")
}

dependencies {
    api(platform(libs.xtext.bom))
    api(libs.ecore)
    api(libs.xtext.core)
    api(libs.xtext.xbase)
    api(project(":oxsts.model"))

    testFixturesApi(libs.xtext.testing)

    mwe2(libs.xtext.generator)
    mwe2(libs.xtext.generator.antlr)
}

val syncModel by tasks.registering(Sync::class) {
    from("../oxsts.model/model")
    into("model")
}

val generateXtextLanguage by tasks.registering(JavaExec::class) {
    mainClass.set("org.eclipse.emf.mwe2.launch.runtime.Mwe2Launcher")
    classpath(configurations.mwe2)

    inputs.files(syncModel.get().outputs)

    inputs.file("src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2")
    inputs.file("src/main/java/hu/bme/mit/semantifyr/oxsts/lang/Oxsts.xtext")

    outputs.dir("src/main/xtext-gen")
    outputs.dir("src/test/java")
    outputs.dir("src/testFixtures/xtext-gen")
    outputs.dir(layout.buildDirectory.dir("generated/sources/xtext/ide"))

    args("src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2", "-p", "rootPath=/$projectDir/..")
}

tasks {
    jar {
        from(sourceSets.main.map { it.allSource }) {
            include("**/*.xtext")
        }
    }

    for (taskName in listOf("compileJava", "processResources", "generateEclipseSourceFolders", "processTestFixturesResources")) {
        named(taskName) {
            inputs.files(generateXtextLanguage.get().outputs)
        }
    }

    clean {
        delete("src/main/xtext-gen")
    }
}

eclipse.project.natures.plusAssign("org.eclipse.xtext.ui.shared.xtextNature")
