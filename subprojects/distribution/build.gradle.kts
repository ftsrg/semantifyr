/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
    distribution
    kotlin("jvm")
}

val launchers = configurations.dependencyScope("launchers")
val launcherFiles = configurations.resolvable("launcherFiles") {
    extendsFrom(launchers.get())
    attributes(semantifyrArtifact("launcher"))
}

dependencies {
    launchers(project(":cli"))
    launchers(project(":sysml-cli"))
    launchers(project(":gamma-cli"))
}

val rootDirectory = isolated.rootProject.projectDirectory

distributions.main {
    distributionBaseName = "semantifyr"
    contents {
        from(launcherFiles) {
            // the launchers share most of their jars
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            filesMatching("bin/*") {
                permissions {
                    unix("rwxr-xr-x")
                }
            }
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
                implementation(project(":theta-executor"))
                implementation(libs.kotlinx.serialization.json)
            }

            targets.all {
                testTask.configure {
                    inputs.files(unzipDistribution)
                    inputs.dir(rootDirectory.dir("oxsts-test-models"))
                        .withPropertyName("oxstsTestModels")
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                    inputs.dir(rootDirectory.dir("subprojects/frontends/sysml/models/tutorial"))
                        .withPropertyName("sysmlTutorialModels")
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                    inputs.dir(rootDirectory.dir("subprojects/frontends/gamma/models/examples"))
                        .withPropertyName("gammaExamples")
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                }
            }
        }
    }
}
