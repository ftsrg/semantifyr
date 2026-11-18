/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.suites")
}

// the Theta the distribution ships
val theta = configurations.dependencyScope("theta")
val thetaFiles = configurations.resolvable("thetaFiles") {
    extendsFrom(theta.get())
    attributes(semantifyrArtifact("theta-xsts-cli"))
}

dependencies {
    theta(project(":theta-executor"))
}

val cloneDistributionTheta = tasks.register<Sync>("cloneDistributionTheta") {
    from(thetaFiles)
    into(layout.buildDirectory.dir("distribution-theta-xsts-cli"))
}

distributions.main {
    contents {
        from(cloneDistributionTheta) {
            into("theta-xsts-cli")
            // archives get fixed permissions, which drop the launcher's execute bit
            filePermissions {
                unix("rwxr-xr-x")
            }
        }
    }
}

// the Windows script keeps its setlocal scope until java exits, so PATH reaches java but not the caller
tasks.startScripts {
    val script = unixScript
    val windowsScriptFile = windowsScript
    doLast {
        val text = script.readText()
        check("\nCLASSPATH=" in text) {
            "${script.name} has no CLASSPATH line"
        }
        script.writeText(text.replaceFirst("\nCLASSPATH=", "\nPATH=\$APP_HOME/theta-xsts-cli:\$PATH\nexport PATH\n\nCLASSPATH="))

        val windowsLaunch = "@rem endlocal doesn't take effect until after the line is parsed and variables are expanded\r\n@rem which allows us to clear the local environment before executing the java command\r\nendlocal & \"%JAVA_EXE%\""
        val windowsText = windowsScriptFile.readText()
        check(windowsLaunch in windowsText) {
            "${windowsScriptFile.name} has no endlocal launch line"
        }
        windowsScriptFile.writeText(
            windowsText.replace(windowsLaunch, "set \"PATH=%APP_HOME%\\theta-xsts-cli;%PATH%\"\r\nset \"CLASSPATH=\" & \"%JAVA_EXE%\""),
        )
    }
}

tasks.distTar {
    enabled = false
}

val unzipDistribution = tasks.register<Sync>("unzipDistribution") {
    inputs.files(tasks.distZip)
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
                    inputs.files(unzipDistribution)
                }
            }
        }
    }
}
