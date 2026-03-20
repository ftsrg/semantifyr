/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    alias(libs.plugins.kotlin.jvm)
}

val thetaXstsCliVersion: String by project
val thetaCliOutputDir = project.layout.buildDirectory.dir("theta-xsts-cli")

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

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository {
            ivy {
                name = "Theta GitHub Releases"
                url = uri("https://github.com/ftsrg/theta/releases/download/")
                patternLayout {
                    artifact("v[revision]/[artifact].[ext]")
                }
                metadataSources { artifact() }
            }
        }
        filter { includeGroup("hu.bme.mit.ftsrg.theta") }
    }
}

val thetaCliJarClasspath by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
    isCanBeResolved = true
}

val thetaNativeLibs by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    api(project(":cex.lang"))
    api(libs.guice)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport)

    thetaCliJarClasspath("hu.bme.mit.ftsrg.theta:theta-xsts-cli:${thetaXstsCliVersion}@jar")

    // Z3 — available on all platforms and architectures
    thetaNativeLibs("org.sosy-lab:javasmt-solver-z3:4.14.0:libz3-$currentArch@$nativeExt")
    thetaNativeLibs("org.sosy-lab:javasmt-solver-z3:4.14.0:libz3java-$currentArch@$nativeExt")

    when (currentOs) {
        "linux" if currentArch == "x64" -> {
            thetaNativeLibs("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:libmathsat5j-x64@so")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-cvc5:1.2.1-g8594a8e4dc:libcvc5jni-x64@so")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-bitwuzla:0.7.0-13.1-g595512ae:libbitwuzlaj-x64@so")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-opensmt:2.9.0-gef441e1c:libopensmtj-x64@so")
//            thetaNativeLibs("org.sosy-lab:javasmt-solver-cvc4:1.8-prerelease-2020-06-24-g7825d8f28:libcvc4-x64@so")
//            thetaNativeLibs("org.sosy-lab:javasmt-solver-cvc4:1.8-prerelease-2020-06-24-g7825d8f28:libcvc4jni-x64@so")
//            thetaNativeLibs("org.sosy-lab:javasmt-solver-cvc4:1.8-prerelease-2020-06-24-g7825d8f28:libcvc4parser-x64@so")
        }
        "windows" if currentArch == "x64" -> {
            thetaNativeLibs("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mathsat5j-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mathsat-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mpir-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-cvc5:1.2.1-g8594a8e4dc:libcvc5jni-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-bitwuzla:0.7.0-13.1-g595512ae:libbitwuzlaj-x64@dll")
        }
    }
}

val syncThetaJar by tasks.registering(Sync::class) {
    from(thetaCliJarClasspath)
    into(thetaCliOutputDir.get().dir("jars"))
    rename { "theta-xsts-cli.jar" }
}

val syncThetaLibs by tasks.registering(Sync::class) {
    from(thetaNativeLibs)
    into(thetaCliOutputDir.get().dir("lib"))
}

val syncThetaScripts by tasks.registering(Sync::class) {
    from(project.layout.projectDirectory.file("scripts"))
    into(thetaCliOutputDir.get())
    preserve {
        include("lib", "jars")
    }
}

val prepareThetaXstsCli by tasks.registering {
    dependsOn(syncThetaJar)
    dependsOn(syncThetaLibs)
    dependsOn(syncThetaScripts)
}
