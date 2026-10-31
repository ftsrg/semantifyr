/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    kotlin("jvm")
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":portfolios"))

    implementation(libs.clikt)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project(":verifier")))
}

application {
    mainClass = "hu.bme.mit.semantifyr.cli.SemantifyrCliKt"
    applicationName = "semantifyr"
}

val rootDirectory = isolated.rootProject.projectDirectory

tasks.named("test") {
    inputs.dir(rootDirectory.dir("oxsts-test-models"))
        .withPropertyName("oxstsTestModels")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

distributions.main {
    contents {
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

// the CLI finds Theta on PATH; the Windows script cannot export PATH past its endlocal
tasks.startScripts {
    val script = unixScript
    doLast {
        val text = script.readText()
        check("\nCLASSPATH=" in text) { "${script.name} has no CLASSPATH line" }
        script.writeText(text.replaceFirst("\nCLASSPATH=", "\nPATH=\$APP_HOME/theta-xsts-cli:\$PATH\nexport PATH\n\nCLASSPATH="))
    }
}

tasks.distTar {
    enabled = false
}
