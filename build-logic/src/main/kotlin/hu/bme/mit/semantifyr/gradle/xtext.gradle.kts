/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hu.bme.mit.semantifyr.gradle.mwe2")
}

val libs = the<LibrariesForLibs>()

val mwe2 = configurations.named<DependencyScopeConfiguration>("mwe2")

dependencies {
    api(libs.slf4j.api)
    api(platform(libs.xtext.bom))
    api(libs.xtext.core)

    implementation(libs.xtext.xbase)

    testFixturesApi(libs.xtext.testing)

    mwe2(libs.xtext.generator)
    "mwe2"(libs.xtext.generator.antlr)
}

val extension = extensions.create<XtextPluginConfiguration>("xtext").apply {
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

val generateXtextLanguage = tasks.register<JavaExec>("generateXtextLanguage") {
    group = "build"

    classpath(configurations.named("mwe2Classpath"))
    mainClass.set("org.eclipse.emf.mwe2.launch.runtime.Mwe2Launcher")

    inputs.file(extension.mweFile)
    inputs.file(extension.xtextFile)

    outputs.dir(extension.genPath)
    outputs.dir(extension.testFixtureGenPath)
    outputs.dir(extension.ideOutput)

    val mweFile = extension.mweFile
    val rootPath = "rootPath=/$projectDir/.."
    argumentProviders += CommandLineArgumentProvider { listOf(mweFile.get(), "-p", rootPath) }
}

configurations.consumable("ideGeneratedElements") {
    attributes(semantifyrArtifact("xtext-ide-sources"))
    outgoing.artifact(extension.ideOutput) {
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
        inputs.files(generateXtextLanguage)
    }

    processResources {
        inputs.files(generateXtextLanguage)
    }

    clean {
        delete(extension.genPath)
        delete(extension.testFixtureGenPath)
    }
}

interface XtextPluginConfiguration {
    val genPath: DirectoryProperty
    val testFixtureGenPath: DirectoryProperty
    val mweFile: Property<String>
    val xtextFile: Property<String>
    val ideOutput: DirectoryProperty
}
