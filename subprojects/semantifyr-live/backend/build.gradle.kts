/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.commitHash
import hu.bme.mit.semantifyr.gradle.runsWithTheta

plugins {
    id("hu.bme.mit.semantifyr.cli")
    id("hu.bme.mit.semantifyr.suites")
    id("hu.bme.mit.semantifyr.theta")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val frontendDistribution = artifactsOf("web-distribution", ":semantifyr-live-frontend")
val gammaCompiledExamples = artifactsOf("compiled-examples", ":gamma-frontend")
val sysmlCompiledExamples = artifactsOf("compiled-examples", ":sysml-wrapper")
val sysmlWorker = artifactsOf("sysml-worker", ":sysml-wrapper")
val gammaLibrary = artifactsOf("gamma-library", ":gamma-frontend")
val sysmlLibrary = artifactsOf("sysml-library", ":sysml-frontend")
val gammaExamples = artifactsOf("gamma-examples", ":gamma-frontend")
val theta = artifactsOf("theta-xsts-cli", ":theta-executor")

dependencies {
    implementation(libs.bundles.ktor.server)
    implementation(libs.micrometer.registry.prometheus)
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

    testFixturesApi(project(":lang-ide-common"))
    testFixturesApi(libs.lsp4j)
    testFixturesApi(libs.lsp4j.jsonrpc)
    testFixturesApi(libs.kotlinx.coroutines.core)
    testFixturesApi(libs.kotlinx.serialization.json)
    testFixturesApi(libs.bundles.ktor.server)
    testFixturesApi(libs.bundles.ktor.client.test)
    testFixturesImplementation(libs.guice)
    testFixturesApi(testFixtures(project(":verifier")))
}

application {
    applicationName = "semantifyr-live-backend"
    mainClass = "hu.bme.mit.semantifyr.live.backend.SemantifyrLiveCliKt"
}

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outputDirectory = layout.buildDirectory.dir("generated/buildinfo")
    val version = project.version.toString()
    val commitHash = commitHash()
    inputs.property("version", version)
    inputs.property("commitHash", commitHash)
    outputs.dir(outputDirectory)

    doLast {
        outputDirectory.get().asFile.mkdirs()
        outputDirectory.get().file("build-info.properties").asFile.writeText(
            "version=$version\ncommit=${commitHash.get()}\n",
        )
    }
}

sourceSets.main {
    resources.srcDir(generateBuildInfo)
}

val cloneSemanticLibraries = tasks.register<Sync>("cloneSemanticLibraries") {
    from(sysmlLibrary) {
        into("sysmlv2")
    }
    from(gammaLibrary) {
        into("gamma")
    }
    into(layout.buildDirectory.dir("staging/semantic-libraries"))
}

val cloneGammaTestModels = tasks.register<Sync>("cloneGammaTestModels") {
    from(gammaExamples)
    into(layout.buildDirectory.dir("staging/gamma-test-models"))
}

val cloneOxstsTestModels = tasks.register<Sync>("cloneOxstsTestModels") {
    from(isolated.rootProject.projectDirectory.dir("oxsts-test-models/smoke"))
    into(layout.buildDirectory.dir("staging/oxsts-test-models"))
}

val cloneGammaLibraryModels = tasks.register<Sync>("cloneGammaLibraryModels") {
    from(gammaCompiledExamples)
    into(layout.buildDirectory.dir("staging/gamma-library-models"))
}

val cloneSysmlLibraryModels = tasks.register<Sync>("cloneSysmlLibraryModels") {
    from(sysmlCompiledExamples)
    into(layout.buildDirectory.dir("staging/sysml-library-models"))
}

val cloneWebDistributions = tasks.register<Sync>("cloneWebDistributions") {
    from(frontendDistribution)
    from(sysmlWorker) {
        exclude("sysml.tmLanguage.json")
        into("sysml-worker")
    }
    into(layout.buildDirectory.dir("staging/web"))
}

val stagingDirectory = layout.buildDirectory.dir("staging").get().asFile

fun JavaExec.servesLive() {
    runsWithTheta()
    inputs.files(cloneSemanticLibraries)

    args = listOf("start")

    environment("SEMANTIFYR_LIVE_SEMANTIC_LIBRARIES_DIR", stagingDirectory.resolve("semantic-libraries").absolutePath)
    environment("SEMANTIFYR_LIVE_PORT", "18080")
    environment("SEMANTIFYR_LIVE_METRICS_PORT", "18081")
    environment("SEMANTIFYR_LIVE_ROOT_WORK_DIR", layout.buildDirectory.dir("work").get().asFile.absolutePath)
}

tasks.named<JavaExec>("run") {
    servesLive()
    inputs.files(cloneWebDistributions)

    environment("SEMANTIFYR_LIVE_WEB_ROOT_DIR", stagingDirectory.resolve("web").absolutePath)
}

tasks.register<JavaExec>("runDev") {
    group = ApplicationPlugin.APPLICATION_GROUP

    servesLive()

    classpath = sourceSets["main"].runtimeClasspath
    mainClass = application.mainClass
    jvmArgs(application.applicationDefaultJvmArgs)
}

val relativeStagingDirectory = "build/staging"

fun Test.readsSharedModels() {
    inputs.files(cloneSemanticLibraries).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGammaTestModels).withPathSensitivity(PathSensitivity.RELATIVE)

    systemProperty("semantifyr.live.semanticLibraries", "$relativeStagingDirectory/semantic-libraries")
    systemProperty("semantifyr.live.gammaTestModels", "$relativeStagingDirectory/gamma-test-models")
}

tasks.named<Test>("test") {
    readsSharedModels()
}

testing {
    suites {
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project(":logging"))
                implementation(project(":oxsts.lang.ide"))
            }
        }
    }
}

tasks.named<Test>("integrationTest") {
    readsSharedModels()
    inputs.files(cloneOxstsTestModels)
    inputs.files(cloneGammaLibraryModels)
    inputs.files(cloneSysmlLibraryModels)

    systemProperty("semantifyr.live.oxstsTestModels", "$relativeStagingDirectory/oxsts-test-models")
    systemProperty("semantifyr.live.gammaLibraryModels", "$relativeStagingDirectory/gamma-library-models")
    systemProperty("semantifyr.live.sysmlLibraryModels", "$relativeStagingDirectory/sysml-library-models")
}

val liveImageContext = tasks.register<Tar>("liveImageContext") {
    archiveFileName = "live-image-context.tar.gz"
    compression = Compression.GZIP
    // the image runs the start script and theta-xsts-cli, whose execute bits Gradle's archives drop by default
    useFileSystemPermissions()
    from(layout.projectDirectory.file("Dockerfile"))
    from(tasks.installDist) {
        into("build/install/${application.applicationName}")
    }
    from(cloneSemanticLibraries) {
        into("build/staging/semantic-libraries")
    }
    from(cloneWebDistributions) {
        into("build/staging/web")
    }
    from(theta) {
        into("build/shipped-theta-xsts-cli")
    }
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(liveImageContext)
}
