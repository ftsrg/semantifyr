/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.the

plugins {
    id("hu.bme.mit.semantifyr.gradle.mwe2")
}

val mwe2 by configurations.getting

val ideGeneratedOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val libs = the<LibrariesForLibs>()

dependencies {
    api(libs.slf4j.api)
    api(platform(libs.xtext.bom))
    api(libs.xtext.core)

    implementation(libs.xtext.xbase)

    testFixturesApi(libs.xtext.testing)

    mwe2(libs.xtext.generator)
    mwe2(libs.xtext.generator.antlr)
}

val extension = extensions.create("xtext", XtextPluginConfiguration::class.java).apply {
    genPath.convention(layout.projectDirectory.dir("src/main/xtext-gen"))
    testFixtureGenPath.convention(layout.projectDirectory.dir("src/testFixtures/xtext-gen"))
    ideOutput.convention(layout.buildDirectory.dir("generated/sources/xtext/ide"))
}

sourceSets {
    main {
        java.srcDir(extension.genPath)
        resources.srcDir(extension.genPath)
    }
    testFixtures {
        java.srcDir(extension.testFixtureGenPath)
        resources.srcDir(extension.testFixtureGenPath)
    }
}

val generateXtextLanguage by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Generates the Xtext language infrastructure"

    classpath(mwe2)
    mainClass.set("org.eclipse.emf.mwe2.launch.runtime.Mwe2Launcher")

    inputs.file(extension.mweFile)
    inputs.file(extension.xtextFile)

    outputs.dir(extension.genPath)
    outputs.dir(extension.testFixtureGenPath)
    outputs.dir(extension.ideOutput)

    args(extension.mweFile.asFile.get(), "-p", "rootPath=/$projectDir/..")
}

artifacts {
    add(ideGeneratedOutput.name, extension.ideOutput) {
        builtBy(generateXtextLanguage)
    }
}

tasks {
    jar {
        from(sourceSets.main.map { it.allSource }) {
            include("**/*.xtext")
        }
    }

    compileJava {
        inputs.files(generateXtextLanguage.get().outputs)
    }

    processResources {
        inputs.files(generateXtextLanguage.get().outputs)
    }

    clean {
        delete(extension.genPath)
        delete(extension.testFixtureGenPath)
    }
}

interface XtextPluginConfiguration {
    val genPath: DirectoryProperty
    val testFixtureGenPath: DirectoryProperty
    val mweFile: RegularFileProperty
    val xtextFile: RegularFileProperty
    val ideOutput: DirectoryProperty
}
