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
    exclusiveContent {
        forRepository {
            ivy {
                name = "Theta GitHub Raw"
                url = uri("https://raw.githubusercontent.com/ftsrg/theta/")
                patternLayout {
                    artifact("v[revision]/lib/[artifact].[ext]")
                }
                metadataSources { artifact() }
            }
        }
        filter { includeGroup("hu.bme.mit.ftsrg.theta.lib") }
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

val thetaZ3LegacyLibs by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
    isCanBeResolved = true
}

val thetaOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
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
        }
        "windows" if currentArch == "x64" -> {
            thetaNativeLibs("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mathsat5j-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mathsat-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-mathsat5:5.6.11-sosy1:mpir-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-cvc5:1.2.1-g8594a8e4dc:libcvc5jni-x64@dll")
            thetaNativeLibs("org.sosy-lab:javasmt-solver-bitwuzla:0.7.0-13.1-g595512ae:libbitwuzlaj-x64@dll")
        }
    }

    // Z3 legacy — a custom Z3 4.5.0 fork with renamed library symbols, committed to the theta repo
    thetaZ3LegacyLibs("hu.bme.mit.ftsrg.theta.lib:libz3legacy:${thetaXstsCliVersion}@$nativeExt")
    thetaZ3LegacyLibs("hu.bme.mit.ftsrg.theta.lib:libz3javalegacy:${thetaXstsCliVersion}@$nativeExt")
}

val prepareThetaXstsCli by tasks.registering(Sync::class) {
    from(thetaNativeLibs) {
        into("lib")
        val arch = currentArch
        rename { filename ->
            val ext = filename.substringAfterLast('.')
            val base = filename.substringBeforeLast('.').substringBeforeLast("-$arch")
            "${base.substringAfterLast('-')}.$ext"
        }
    }

    from(thetaZ3LegacyLibs) {
        into("lib")
        rename { filename ->
            val ext = filename.substringAfterLast('.')
            "${filename.substringBeforeLast('.').substringBeforeLast('-')}.$ext"
        }
    }

    from(thetaCliJarClasspath) {
        into("jars")
        rename { "theta-xsts-cli.jar" }
    }

    from(project.layout.projectDirectory.file("scripts"))

    into(project.layout.buildDirectory.dir("theta-xsts-cli"))
}

artifacts {
    add(thetaOutput.name, layout.buildDirectory.dir("theta-xsts-cli")) {
        builtBy(prepareThetaXstsCli)
    }
}
