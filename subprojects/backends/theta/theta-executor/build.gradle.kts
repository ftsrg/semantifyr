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

val thetaXstsCliVersion = providers.gradleProperty("thetaXstsCliVersion").get()

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
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport)

    thetaCli("hu.bme.mit.ftsrg.theta:theta-xsts-cli:$thetaXstsCliVersion@jar")

    // Z3 - available on all platforms and architectures
    thetaNativeLibraries("org.sosy-lab:javasmt-solver-z3:4.14.0:libz3-$currentArchitecture@$nativeExtension")
    thetaNativeLibraries("org.sosy-lab:javasmt-solver-z3:4.14.0:libz3java-$currentArchitecture@$nativeExtension")

    when (currentOs) {
        "linux" if currentArchitecture == "x64" -> {
            thetaNativeLibraries("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:libmathsat5j-x64@so")
            thetaNativeLibraries("org.sosy-lab:javasmt-solver-cvc5:1.2.1-g8594a8e4dc:libcvc5jni-x64@so")
            thetaNativeLibraries("org.sosy-lab:javasmt-solver-bitwuzla:0.7.0-13.1-g595512ae:libbitwuzlaj-x64@so")
            thetaNativeLibraries("org.sosy-lab:javasmt-solver-opensmt:2.9.0-gef441e1c:libopensmtj-x64@so")
        }

        "windows" if currentArchitecture == "x64" -> {
            thetaNativeLibraries("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mathsat5j-x64@dll")
            thetaNativeLibraries("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mathsat-x64@dll")
            thetaNativeLibraries("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mpir-x64@dll")
            thetaNativeLibraries("org.sosy-lab:javasmt-solver-cvc5:1.2.1-g8594a8e4dc:libcvc5jni-x64@dll")
            thetaNativeLibraries("org.sosy-lab:javasmt-solver-bitwuzla:0.7.0-13.1-g595512ae:libbitwuzlaj-x64@dll")
        }
    }

    // Z3 legacy - a custom Z3 4.5.0 fork with renamed library symbols, committed to the theta repo
    thetaZ3LegacyLibraries("hu.bme.mit.ftsrg.theta.lib:libz3legacy:$thetaXstsCliVersion@$nativeExtension")
    thetaZ3LegacyLibraries("hu.bme.mit.ftsrg.theta.lib:libz3javalegacy:$thetaXstsCliVersion@$nativeExtension")
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
