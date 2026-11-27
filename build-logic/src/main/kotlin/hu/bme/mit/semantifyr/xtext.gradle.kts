/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import hu.bme.mit.semantifyr.gradle.javaRuntimeLibrary
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hu.bme.mit.semantifyr.jvm")
}

val libs = the<LibrariesForLibs>()

val mwe2 = configurations.dependencyScope("mwe2")
val mwe2Classpath = configurations.resolvable("mwe2Classpath") {
    extendsFrom(configurations.implementation.get(), mwe2.get())
    attributes(javaRuntimeLibrary())
}

dependencies {
    mwe2(libs.mwe2.launch)
    mwe2(libs.xtext.generator)
    mwe2(libs.xtext.generator.antlr)
    mwe2(libs.xtext.xbase)
    mwe2(libs.gson)

    api(platform(libs.xtext.bom))
    api(libs.antlr.runtime)
    api(libs.ecore)
    api(libs.emf.common)
    api(libs.guice)
    api(libs.xtext.core)
    api(libs.xtext.util)
}

val extension = extensions.create<XtextPluginConfiguration>("xtext")

val xtextGeneratedDirectory = layout.projectDirectory.dir("src/main/xtext-gen")
val testFixturesXtextGeneratedDirectory = layout.projectDirectory.dir("src/testFixtures/xtext-gen")
val ideGeneratedDirectory = layout.buildDirectory.dir("generated/sources/xtext/ide")

val generateXtextLanguage = tasks.register<JavaExec>("generateXtextLanguage") {
    group = LifecycleBasePlugin.BUILD_GROUP

    classpath(mwe2Classpath)
    mainClass = "org.eclipse.emf.mwe2.launch.runtime.Mwe2Launcher"

    inputs.file(extension.mweFile).withPathSensitivity(PathSensitivity.NONE)
    inputs.file(extension.xtextFile).withPathSensitivity(PathSensitivity.NONE)

    outputs.dir(xtextGeneratedDirectory)
    outputs.dir(testFixturesXtextGeneratedDirectory)
    outputs.dir(ideGeneratedDirectory)

    val mweFile = extension.mweFile
    val rootPath = "rootPath=/$projectDir/.."
    argumentProviders += CommandLineArgumentProvider {
        listOf(mweFile.get(), "-p", rootPath)
    }

    outputs.cacheIf {
        true
    }
}

sourceSets {
    main {
        java.srcDir(files(xtextGeneratedDirectory).builtBy(generateXtextLanguage))
        resources.srcDir(files(xtextGeneratedDirectory).builtBy(generateXtextLanguage))
    }
    testFixtures {
        java.srcDir(files(testFixturesXtextGeneratedDirectory).builtBy(generateXtextLanguage))
        resources.srcDir(files(testFixturesXtextGeneratedDirectory).builtBy(generateXtextLanguage))
    }
}

configurations.consumable("xtextIdeSourcesElements") {
    attributes(semantifyrArtifact("xtext-ide-sources"))
    outgoing.artifact(ideGeneratedDirectory) {
        builtBy(generateXtextLanguage)
    }
}

tasks.clean {
    delete(xtextGeneratedDirectory)
    delete(testFixturesXtextGeneratedDirectory)
}

interface XtextPluginConfiguration {
    val mweFile: Property<String>
    val xtextFile: Property<String>
}
