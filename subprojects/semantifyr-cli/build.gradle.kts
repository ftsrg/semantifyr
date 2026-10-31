/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
    kotlin("jvm")
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":portfolios"))

    implementation(libs.clikt)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":verifier")))
}

application {
    mainClass = "hu.bme.mit.semantifyr.cli.SemantifyrCliKt"
    applicationName = "semantifyr"
    // Mordant loads its terminal library natively, Guice's bytecode generation calls sun.misc.Unsafe; both warn on every run
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-Dguice_bytecode_gen_option=DISABLED")
}

val rootDirectory = isolated.rootProject.projectDirectory

tasks.named("test") {
    inputs.dir(rootDirectory.dir("oxsts-test-models"))
        .withPropertyName("oxstsTestModels")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

testing {
    suites {
        named<JvmTestSuite>("verificationTest") {
            dependencies {
                implementation(project(":theta-executor"))
                implementation(libs.kotlinx.serialization.json)
            }

            targets.all {
                testTask.configure {
                    inputs.dir(rootDirectory.dir("oxsts-test-models"))
                        .withPropertyName("oxstsTestModels")
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                    inputs.files(tasks.installDist)
                }
            }
        }
    }
}

// cmd.exe rejects a line over 8191 characters, which the expanded jar list exceeds under a long install path
val windowsClasspathJar = tasks.register<Jar>("windowsClasspathJar") {
    val classPath = files(tasks.jar, configurations.runtimeClasspath).elements.map { elements ->
        elements.joinToString(" ") { it.asFile.name }
    }
    inputs.property("classPath", classPath)
    archiveFileName = "semantifyr-classpath.jar"
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
        from(rootDirectory.dir("subprojects/frontends/gamma/models/libraries/default")) {
            into("libraries/gamma")
        }
        from(rootDirectory.dir("subprojects/frontends/sysml/models/libraries/default")) {
            into("libraries/sysmlv2")
        }
        from(rootDirectory.dir("oxsts-test-models/smoke")) {
            into("models/smoke")
        }
    }
}

// the CLI finds Theta on PATH; the Windows script keeps its setlocal scope until java exits, so PATH reaches java but not the caller
tasks.startScripts {
    val script = unixScript
    val windowsScriptFile = windowsScript
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
                .replace(windowsClasspath) { "\r\nset CLASSPATH=%APP_HOME%\\lib\\semantifyr-classpath.jar" }
                .replace(windowsLaunch, "set \"PATH=%APP_HOME%\\theta-xsts-cli;%PATH%\"\r\nset \"CLASSPATH=\" & \"%JAVA_EXE%\""),
        )
    }
}

tasks.distTar {
    enabled = false
}
