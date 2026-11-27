/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

plugins {
    id("hu.bme.mit.semantifyr.cli")
    id("hu.bme.mit.semantifyr.theta")
    id("hu.bme.mit.semantifyr.suites")
}

dependencies {
    implementation(project(":cli-common"))
}

val unzipDistribution = tasks.register<Sync>("unzipDistribution") {
    val distributionArchive = tasks.distZip.flatMap {
        it.archiveFile
    }
    from(zipTree(distributionArchive))
    into(layout.buildDirectory.dir("unzipped"))
}

testing {
    suites {
        named<JvmTestSuite>("endToEndTest") {
            dependencies {
                implementation(testFixtures(project(":cli-common")))
            }

            targets.all {
                testTask.configure {
                    inputs.files(unzipDistribution).withPathSensitivity(PathSensitivity.RELATIVE)
                }
            }
        }
    }
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(tasks.distZip)
}
