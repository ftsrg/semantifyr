/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    alias(libs.plugins.bmuschko.docker)
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

val lspDistributions by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val frontendDistribution by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val thetaClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lsp4j)
    implementation(libs.lsp4j.jsonrpc)
    implementation(libs.clikt)
    implementation(libs.guice)
    implementation(libs.guice.extensions.assistedinject)
    implementation(libs.slf4j.api)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)

    runtimeOnly(libs.slf4j.log4j)

    lspDistributions(project(":oxsts.lang.ide", configuration = "distributionOutput"))
    lspDistributions(project(":xsts.lang.ide", configuration = "distributionOutput"))
    lspDistributions(project(":gamma.lang.ide", configuration = "distributionOutput"))

    frontendDistribution(project(":semantifyr-live-frontend", configuration = "distributionOutput"))

    thetaClasspath(project(":theta-wrapper", configuration = "thetaOutput"))
}

application {
    applicationName = "semantifyr-live-backend"
    mainClass = "hu.bme.mit.semantifyr.live.backend.SemantifyrLiveCliKt"
}

val cloneLspDistributions by tasks.registering(Sync::class) {
    from(lspDistributions)
    into(layout.buildDirectory.dir("staging/lsp"))
}

val cloneWebDistributions by tasks.registering(Sync::class) {
    from(frontendDistribution)
    into(layout.buildDirectory.dir("staging/web"))
}

val cloneTheta by tasks.registering(Sync::class) {
    inputs.files(thetaClasspath)
    from(thetaClasspath)
    into(layout.buildDirectory.dir("theta-xsts-cli"))
}

tasks.named<JavaExec>("run") {
    group = "application"
    description = "Run the backend with web root (builds frontend)"

    inputs.files(cloneLspDistributions)
    inputs.files(cloneWebDistributions)

    val stagingDir = layout.buildDirectory.dir("staging").get().asFile
    val workDir = layout.buildDirectory.dir("work").get().asFile

    args = listOf("start")

    environment("SEMANTIFYR_LIVE_LSP_BINARIES_DIR", stagingDir.resolve("lsp").absolutePath)
    environment("SEMANTIFYR_LIVE_WEB_ROOT_DIR", stagingDir.resolve("web").absolutePath)
    environment("SEMANTIFYR_LIVE_PORT", "18080")
    environment("SEMANTIFYR_LIVE_ROOT_WORK_DIR", workDir.absolutePath)
    environment(
        "SEMANTIFYR_LIVE_ALLOWED_ORIGINS",
        "localhost:5173,localhost:3000,localhost:18080",
    )
}

val runDev by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Run the backend without web root (for use with Vite dev/preview)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = application.mainClass

    inputs.files(cloneLspDistributions)

    val stagingDir = layout.buildDirectory.dir("staging").get().asFile
    val workDir = layout.buildDirectory.dir("work").get().asFile

    args = listOf("start")

    environment("SEMANTIFYR_LIVE_LSP_BINARIES_DIR", stagingDir.resolve("lsp").absolutePath)
    environment("SEMANTIFYR_LIVE_PORT", "18080")
    environment("SEMANTIFYR_LIVE_ROOT_WORK_DIR", workDir.absolutePath)
    environment(
        "SEMANTIFYR_LIVE_ALLOWED_ORIGINS",
        "localhost:5173,localhost:3000,localhost:18080",
    )
}

val endToEndTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Run backend end-to-end tests (boot real LSP, drive verification over WS)"

    inputs.files(cloneLspDistributions)
    inputs.files(cloneWebDistributions)

    useJUnitPlatform {
        includeTags("slow")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    val stagingDir = layout.buildDirectory.dir("staging").get().asFile
    systemProperty("semantifyr.live.lsp", stagingDir.resolve("lsp").absolutePath)

    minHeapSize = "512m"
    maxHeapSize = "4G"
    testLogging.showStandardStreams = true
}

val prepareDocker by tasks.registering {
    inputs.files(cloneLspDistributions)
    inputs.files(cloneWebDistributions)
    inputs.files(cloneTheta)
    inputs.files(tasks.installDist)
}

val dockerBuildImage by tasks.registering(DockerBuildImage::class) {
    dependsOn(prepareDocker)
    inputDir.set(projectDir)
    images.add("ftsrgbot/semantifyr-server:${project.version}")
}

val dockerPushImage by tasks.registering(DockerPushImage::class) {
    dependsOn(dockerBuildImage)
    images.add("ftsrgbot/semantifyr-server:${project.version}")
}
