/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.javaRuntimeLibrary
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    kotlin("jvm")
}

val thetaXstsCliVersion = providers.gradleProperty("thetaXstsCliVersion").get()

val currentOs = when {
    System.getProperty("os.name").lowercase().contains("win") -> "windows"
    System.getProperty("os.name").lowercase().contains("mac") -> "osx"
    else -> "linux"
}
val currentArch = when (System.getProperty("os.arch")) {
    "aarch64" -> "arm64"
    else -> "x64"
}
val nativeExt = when (currentOs) {
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

val thetaNativeLibs = configurations.dependencyScope("thetaNativeLibs")
val thetaNativeLibFiles = configurations.resolvable("thetaNativeLibFiles") {
    extendsFrom(thetaNativeLibs.get())
    isTransitive = false
    attributes(javaRuntimeLibrary())
}

val thetaZ3LegacyLibs = configurations.dependencyScope("thetaZ3LegacyLibs")
val thetaZ3LegacyLibFiles = configurations.resolvable("thetaZ3LegacyLibFiles") {
    extendsFrom(thetaZ3LegacyLibs.get())
    isTransitive = false
    attributes(javaRuntimeLibrary())
}

dependencies {
    api(project(":cex.lang"))
    api(project(":backend"))
    api(libs.guice)

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport)

    thetaCli("hu.bme.mit.ftsrg.theta:theta-xsts-cli:$thetaXstsCliVersion@jar")

    // Z3 - available on all platforms and architectures
    thetaNativeLibs("org.sosy-lab:javasmt-solver-z3:4.14.0:libz3-$currentArch@$nativeExt")
    thetaNativeLibs("org.sosy-lab:javasmt-solver-z3:4.14.0:libz3java-$currentArch@$nativeExt")

    when (currentOs) {
        "linux" if currentArch == "x64" -> {
            thetaNativeLibs("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:libmathsat5j-x64@so")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-cvc5:1.2.1-g8594a8e4dc:libcvc5jni-x64@so")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-bitwuzla:0.7.0-13.1-g595512ae:libbitwuzlaj-x64@so")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-opensmt:2.9.0-gef441e1c:libopensmtj-x64@so")
        }

        "windows" if currentArch == "x64" -> {
            thetaNativeLibs("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mathsat5j-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mathsat-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mpir-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-cvc5:1.2.1-g8594a8e4dc:libcvc5jni-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-bitwuzla:0.7.0-13.1-g595512ae:libbitwuzlaj-x64@dll")
        }
    }

    // Z3 legacy - a custom Z3 4.5.0 fork with renamed library symbols, committed to the theta repo
    thetaZ3LegacyLibs("hu.bme.mit.ftsrg.theta.lib:libz3legacy:$thetaXstsCliVersion@$nativeExt")
    thetaZ3LegacyLibs("hu.bme.mit.ftsrg.theta.lib:libz3javalegacy:$thetaXstsCliVersion@$nativeExt")
}

val prepareThetaXstsCli = tasks.register<Sync>("prepareThetaXstsCli") {
    from(thetaNativeLibFiles) {
        into("lib")
        val arch = currentArch
        rename { filename ->
            val ext = filename.substringAfterLast('.')
            val base = filename.substringBeforeLast('.').substringBeforeLast("-$arch")
            "${base.substringAfterLast('-')}.$ext"
        }
    }

    from(thetaZ3LegacyLibFiles) {
        into("lib")
        rename { filename ->
            val ext = filename.substringAfterLast('.')
            "${filename.substringBeforeLast('.').substringBeforeLast('-')}.$ext"
        }
    }

    from(thetaCliFiles) {
        into("jars")
        rename { "theta-xsts-cli.jar" }
    }

    from(project.layout.projectDirectory.file("scripts"))

    into(project.layout.buildDirectory.dir("theta-xsts-cli"))
}

configurations.consumable("thetaXstsCliElements") {
    attributes(semantifyrArtifact("theta-xsts-cli"))
    outgoing.artifact(layout.buildDirectory.dir("theta-xsts-cli")) {
        builtBy(prepareThetaXstsCli)
    }
}
