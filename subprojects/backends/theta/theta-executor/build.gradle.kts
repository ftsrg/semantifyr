/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.javaRuntimeLibrary
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    kotlin("jvm")
}

val operatingSystemName = System.getProperty("os.name").lowercase()
val currentOs = when {
    operatingSystemName.contains("win") -> "windows"
    operatingSystemName.contains("mac") -> "osx"
    else -> "linux"
}
val currentArchitecture = when (System.getProperty("os.arch")) {
    "aarch64" -> "arm64"
    else -> "x64"
}
val nativeExtension = when (currentOs) {
    "windows" -> "dll"
    "osx" -> "dylib"
    else -> "so"
}

val thetaCli = configurations.dependencyScope("thetaCli")
val thetaCliFiles = configurations.resolvable("thetaCliFiles") {
    extendsFrom(thetaCli.get())
    isTransitive = false
    attributes(javaRuntimeLibrary())
}

val thetaNativeLibraries = configurations.dependencyScope("thetaNativeLibraries")
val thetaNativeLibraryFiles = configurations.resolvable("thetaNativeLibraryFiles") {
    extendsFrom(thetaNativeLibraries.get())
    isTransitive = false
    attributes(javaRuntimeLibrary())
}

val thetaZ3LegacyLibraries = configurations.dependencyScope("thetaZ3LegacyLibraries")
val thetaZ3LegacyLibraryFiles = configurations.resolvable("thetaZ3LegacyLibraryFiles") {
    extendsFrom(thetaZ3LegacyLibraries.get())
    isTransitive = false
    attributes(javaRuntimeLibrary())
}

dependencies {
    api(project(":backend"))

    implementation(project(":logging"))

    thetaCli(
        variantOf(libs.theta.xsts.cli) {
            artifactType("jar")
        },
    )

    // Z3 - available on all platforms and architectures
    thetaNativeLibraries(
        variantOf(libs.javasmt.z3) {
            classifier("libz3-$currentArchitecture")
            artifactType(nativeExtension)
        },
    )
    thetaNativeLibraries(
        variantOf(libs.javasmt.z3) {
            classifier("libz3java-$currentArchitecture")
            artifactType(nativeExtension)
        },
    )

    when (currentOs) {
        "linux" if currentArchitecture == "x64" -> {
            thetaNativeLibraries(
                variantOf(libs.javasmt.mathsat5) {
                    classifier("libmathsat5j-x64")
                    artifactType("so")
                },
            )
            thetaNativeLibraries(
                variantOf(libs.javasmt.cvc5) {
                    classifier("libcvc5jni-x64")
                    artifactType("so")
                },
            )
            thetaNativeLibraries(
                variantOf(libs.javasmt.bitwuzla) {
                    classifier("libbitwuzlaj-x64")
                    artifactType("so")
                },
            )
            thetaNativeLibraries(
                variantOf(libs.javasmt.opensmt) {
                    classifier("libopensmtj-x64")
                    artifactType("so")
                },
            )
        }

        "windows" if currentArchitecture == "x64" -> {
            thetaNativeLibraries(
                variantOf(libs.javasmt.mathsat5) {
                    classifier("mathsat5j-x64")
                    artifactType("dll")
                },
            )
            thetaNativeLibraries(
                variantOf(libs.javasmt.mathsat5) {
                    classifier("mathsat-x64")
                    artifactType("dll")
                },
            )
            thetaNativeLibraries(
                variantOf(libs.javasmt.mathsat5) {
                    classifier("mpir-x64")
                    artifactType("dll")
                },
            )
            thetaNativeLibraries(
                variantOf(libs.javasmt.cvc5) {
                    classifier("libcvc5jni-x64")
                    artifactType("dll")
                },
            )
            thetaNativeLibraries(
                variantOf(libs.javasmt.bitwuzla) {
                    classifier("libbitwuzlaj-x64")
                    artifactType("dll")
                },
            )
        }
    }

    // Z3 legacy - a custom Z3 4.5.0 fork with renamed library symbols, committed to the theta repo
    thetaZ3LegacyLibraries(
        variantOf(libs.theta.libz3legacy) {
            artifactType(nativeExtension)
        },
    )
    thetaZ3LegacyLibraries(
        variantOf(libs.theta.libz3javalegacy) {
            artifactType(nativeExtension)
        },
    )
}

val thetaXstsCliDirectory = layout.buildDirectory.dir("theta-xsts-cli")

val prepareThetaXstsCli = tasks.register<Sync>("prepareThetaXstsCli") {
    from(thetaNativeLibraryFiles) {
        into("lib")
        val architecture = currentArchitecture
        rename {
            val extension = it.substringAfterLast('.')
            val base = it.substringBeforeLast('.').substringBeforeLast("-$architecture")
            "${base.substringAfterLast('-')}.$extension"
        }
    }

    from(thetaZ3LegacyLibraryFiles) {
        into("lib")
        rename {
            val extension = it.substringAfterLast('.')
            "${it.substringBeforeLast('.').substringBeforeLast('-')}.$extension"
        }
    }

    from(thetaCliFiles) {
        into("jars")
        rename {
            "theta-xsts-cli.jar"
        }
    }

    from(layout.projectDirectory.dir("scripts"))

    into(thetaXstsCliDirectory)
}

configurations.consumable("thetaXstsCliElements") {
    attributes(semantifyrArtifact("theta-xsts-cli"))
    outgoing.artifact(thetaXstsCliDirectory) {
        builtBy(prepareThetaXstsCli)
    }
}
