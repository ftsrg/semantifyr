/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    application
}

val libs = the<LibrariesForLibs>()

dependencies {
    runtimeOnly(libs.slf4j.log4j)
}

application {
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dguice_bytecode_gen_option=DISABLED",
    )
}

// cmd.exe rejects a line over 8191 characters, which the expanded jar list exceeds under a long install path
val windowsClasspathJar = tasks.register<Jar>("windowsClasspathJar") {
    val classPath = files(tasks.jar, configurations.runtimeClasspath).elements.map { elements ->
        elements.joinToString(" ") {
            it.asFile.name
        }
    }
    inputs.property("classPath", classPath)
    archiveFileName = provider {
        "${application.applicationName}-classpath.jar"
    }
    destinationDirectory = layout.buildDirectory.dir("windows-classpath")
    manifest {
        attributes("Class-Path" to classPath)
    }
}

distributions.main {
    contents {
        from(windowsClasspathJar) {
            into("lib")
        }
    }
}

tasks.startScripts {
    val windowsScriptFile = windowsScript
    val classpathJarName = windowsClasspathJar.flatMap {
        it.archiveFileName
    }
    doLast {
        val windowsClasspath = Regex("\r\nset CLASSPATH=[^\r\n]*")
        val windowsText = windowsScriptFile.readText()
        check(windowsClasspath in windowsText) {
            "${windowsScriptFile.name} has no CLASSPATH line"
        }
        windowsScriptFile.writeText(
            windowsText.replace(windowsClasspath) {
                "\r\nset CLASSPATH=%APP_HOME%\\lib\\${classpathJarName.get()}"
            },
        )
    }
}

tasks.distTar {
    enabled = false
}

tasks.register("pack") {
    dependsOn(tasks.distZip)
}

configurations.consumable("distributionElements") {
    attributes(semantifyrArtifact("application-distribution"))
    outgoing.artifact(layout.buildDirectory.dir("install")) {
        builtBy(tasks.installDist)
    }
}
