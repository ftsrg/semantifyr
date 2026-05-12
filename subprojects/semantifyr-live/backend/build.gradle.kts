/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import java.time.Instant

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.conventions.integration")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    alias(libs.plugins.bmuschko.docker)
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val frontendDistribution by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val gammaCompiledExamples by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val sysmlCompiledExamples by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(libs.bundles.ktor.server)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lsp4j)
    implementation(libs.lsp4j.jsonrpc)
    implementation(libs.clikt)
    implementation(libs.guice)
    implementation(libs.guice.extensions.assistedinject)

    implementation(project(":utils"))
    implementation(project(":logging"))
    implementation(project(":oxsts.lang.ide"))
    implementation(project(":gamma.lang.ide"))

    testFixturesApi(libs.lsp4j)
    testFixturesApi(libs.lsp4j.jsonrpc)
    testFixturesApi(libs.kotlinx.coroutines.core)
    testFixturesApi(libs.kotlinx.serialization.json)
    testFixturesApi(libs.bundles.ktor.server)
    testFixturesApi(libs.bundles.ktor.client.test)
    testFixturesImplementation(libs.guice)

    frontendDistribution(project(":semantifyr-live-frontend", configuration = "distributionOutput"))

    gammaCompiledExamples(project(":gamma-frontend", configuration = "compiledExamples"))
    sysmlCompiledExamples(project(":sysml-wrapper", configuration = "compiledExamples"))
}

application {
    applicationName = "semantifyr-live-backend"
    mainClass = "hu.bme.mit.semantifyr.live.backend.SemantifyrLiveCliKt"
}

val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildinfo")
    outputs.dir(outputDir)

    doLast {
        val commitHash = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .readText()
            .trim()
        val buildTime = Instant.now().toString()

        outputDir.get().asFile.mkdirs()
        outputDir.get().file("build-info.properties").asFile.writeText(
            "commit=$commitHash\nbuildTime=$buildTime\n",
        )
    }
}

sourceSets.main {
    resources.srcDir(generateBuildInfo.map { it.outputs.files.singleFile })
}

val cloneSemanticLibraries by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/libraries/default")) {
        into("sysmlv2")
    }
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/libraries/default")) {
        into("gamma")
    }
    into(layout.buildDirectory.dir("staging/semantic-libraries"))
}

val cloneGammaTestModels by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/examples"))
    into(layout.buildDirectory.dir("staging/gamma-test-models"))
}

val cloneOxstsTestModels by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("oxsts-test-models/simple"))
    into(layout.buildDirectory.dir("staging/oxsts-test-models"))
}

val cloneGammaLibraryModels by tasks.registering(Sync::class) {
    from(gammaCompiledExamples)
    into(layout.buildDirectory.dir("staging/gamma-library-models"))
}

val cloneSysmlLibraryModels by tasks.registering(Sync::class) {
    from(sysmlCompiledExamples)
    into(layout.buildDirectory.dir("staging/sysml-library-models"))
}

val cloneWebDistributions by tasks.registering(Sync::class) {
    from(frontendDistribution)
    into(layout.buildDirectory.dir("staging/web"))
}

val cloneTheta = tasks.named<Sync>("cloneTheta")

tasks.named<JavaExec>("run") {
    group = "application"

    inputs.files(cloneSemanticLibraries)
    inputs.files(cloneWebDistributions)
    inputs.files(cloneTheta)

    val thetaCliDir = cloneTheta.map { it.destinationDir.absolutePath }

    val stagingDir = layout.buildDirectory.dir("staging").get().asFile
    val workDir = layout.buildDirectory.dir("work").get().asFile

    args = listOf("start")

    environment("SEMANTIFYR_LIVE_SEMANTIC_LIBRARIES_DIR", stagingDir.resolve("semantic-libraries").absolutePath)
    environment("SEMANTIFYR_LIVE_WEB_ROOT_DIR", stagingDir.resolve("web").absolutePath)
    environment("SEMANTIFYR_LIVE_PORT", "18080")
    environment("SEMANTIFYR_LIVE_ROOT_WORK_DIR", workDir.absolutePath)
    environment("SEMANTIFYR_LIVE_ADMIN_PASSWORD", "testing")

    doFirst {
        environment("PATH", "${thetaCliDir.get()}${File.pathSeparator}${System.getenv("PATH")}")
    }
}

val runDev by tasks.registering(JavaExec::class) {
    group = "application"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass = application.mainClass

    inputs.files(cloneSemanticLibraries)
    inputs.files(cloneTheta)

    val thetaCliDir = cloneTheta.map { it.destinationDir.absolutePath }

    val stagingDir = layout.buildDirectory.dir("staging").get().asFile
    val workDir = layout.buildDirectory.dir("work").get().asFile

    args = listOf("start")

    environment("SEMANTIFYR_LIVE_SEMANTIC_LIBRARIES_DIR", stagingDir.resolve("semantic-libraries").absolutePath)
    environment("SEMANTIFYR_LIVE_PORT", "18080")
    environment("SEMANTIFYR_LIVE_ROOT_WORK_DIR", workDir.absolutePath)
    environment("SEMANTIFYR_LIVE_ADMIN_PASSWORD", "testing")

    doFirst {
        environment("PATH", "${thetaCliDir.get()}${File.pathSeparator}${System.getenv("PATH")}")
    }
}

testing {
    suites {
        val integrationTest by getting(JvmTestSuite::class) {
            dependencies {
                implementation(project(":logging"))
            }

            targets.all {
                testTask.configure {
                    inputs.files(cloneSemanticLibraries)
                    inputs.files(cloneGammaTestModels)
                    inputs.files(cloneOxstsTestModels)
                    inputs.files(cloneGammaLibraryModels)
                    inputs.files(cloneSysmlLibraryModels)

                    val stagingDir = layout.buildDirectory.dir("staging").get().asFile

                    systemProperty("semantifyr.live.semanticLibraries", stagingDir.resolve("semantic-libraries").absolutePath)
                    systemProperty("semantifyr.live.gammaTestModels", stagingDir.resolve("gamma-test-models").absolutePath)
                    systemProperty("semantifyr.live.oxstsTestModels", stagingDir.resolve("oxsts-test-models").absolutePath)
                    systemProperty("semantifyr.live.gammaLibraryModels", stagingDir.resolve("gamma-library-models").absolutePath)
                    systemProperty("semantifyr.live.sysmlLibraryModels", stagingDir.resolve("sysml-library-models").absolutePath)
                }
            }
        }
    }
}

val prepareDocker by tasks.registering {
    inputs.files(cloneSemanticLibraries)
    inputs.files(cloneWebDistributions)
    inputs.files(cloneTheta)
    inputs.files(tasks.installDist)
}

val dockerImageRepo = "ftsrgbot/semantifyr-server"
val dockerGitSha = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map {
    it.trim()
}

val dockerBuildImage by tasks.registering(DockerBuildImage::class) {
    dependsOn(prepareDocker)
    inputDir.set(projectDir)
    images.add("$dockerImageRepo:${project.version}")
    images.add("$dockerImageRepo:latest")
    images.add(dockerGitSha.map { "$dockerImageRepo:$it" })
}

val dockerPushImage by tasks.registering(DockerPushImage::class) {
    dependsOn(dockerBuildImage)
    images.add("$dockerImageRepo:${project.version}")
    images.add("$dockerImageRepo:latest")
    images.add(dockerGitSha.map { "$dockerImageRepo:$it" })
}
