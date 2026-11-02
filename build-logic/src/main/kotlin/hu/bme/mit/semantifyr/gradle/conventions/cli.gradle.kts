/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
}

// cmd.exe rejects a line over 8191 characters, which the expanded jar list exceeds under a long install path
val windowsClasspathJar = tasks.register<Jar>("windowsClasspathJar") {
    val classPath = files(tasks.jar, configurations.runtimeClasspath).elements.map { elements ->
        elements.joinToString(" ") { it.asFile.name }
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
        from(tasks.named("cloneTheta")) {
            into("theta-xsts-cli")
            // archives get fixed permissions, which drop the launcher's execute bit
            filePermissions {
                unix("rwxr-xr-x")
            }
        }
    }
}

// the launcher finds Theta on PATH; the Windows script keeps its setlocal scope until java exits, so PATH reaches java but not the caller
tasks.startScripts {
    val script = unixScript
    val windowsScriptFile = windowsScript
    val classpathJarName = windowsClasspathJar.flatMap { it.archiveFileName }
    doLast {
        val text = script.readText()
        check("\nCLASSPATH=" in text) { "${script.name} has no CLASSPATH line" }
        script.writeText(text.replaceFirst("\nCLASSPATH=", "\nPATH=\$APP_HOME/theta-xsts-cli:\$PATH\nexport PATH\n\nCLASSPATH="))

        val windowsLaunch = "@rem endlocal doesn't take effect until after the line is parsed and variables are expanded\r\n@rem which allows us to clear the local environment before executing the java command\r\nendlocal & \"%JAVA_EXE%\""
        val windowsClasspath = Regex("\r\nset CLASSPATH=[^\r\n]*")
        val windowsText = windowsScriptFile.readText()
        check(windowsLaunch in windowsText) { "${windowsScriptFile.name} has no endlocal launch line" }
        check(windowsClasspath in windowsText) { "${windowsScriptFile.name} has no CLASSPATH line" }
        windowsScriptFile.writeText(
            windowsText
                .replace(windowsClasspath) { "\r\nset CLASSPATH=%APP_HOME%\\lib\\${classpathJarName.get()}" }
                .replace(windowsLaunch, "set \"PATH=%APP_HOME%\\theta-xsts-cli;%PATH%\"\r\nset \"CLASSPATH=\" & \"%JAVA_EXE%\""),
        )
    }
}

tasks.distTar {
    enabled = false
}

val unzipDistribution = tasks.register<Sync>("unzipDistribution") {
    inputs.files(tasks.distZip)
    from(zipTree(tasks.distZip.flatMap { it.archiveFile }))
    into(layout.buildDirectory.dir("unzipped"))
}

testing {
    suites {
        named<JvmTestSuite>("verificationTest") {
            dependencies {
                implementation(testFixtures(project(":cli-common")))
            }

            targets.all {
                testTask.configure {
                    inputs.files(unzipDistribution)
                }
            }
        }
    }
}
