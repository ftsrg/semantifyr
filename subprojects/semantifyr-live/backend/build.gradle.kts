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
val gammaCompiledSmoke = artifactsOf("compiled-smoke", ":gamma-frontend")
val sysmlCompiledSmoke = artifactsOf("compiled-smoke", ":sysml-wrapper")
val sysmlWorker = artifactsOf("sysml-worker", ":sysml-wrapper")
val gammaLibrary = isolated.rootProject.projectDirectory.dir("subprojects/frontends/gamma/models/libraries/default")
val sysmlLibrary = isolated.rootProject.projectDirectory.dir("subprojects/frontends/sysml/models/libraries/default")
val gammaSmoke = isolated.rootProject.projectDirectory.dir("subprojects/frontends/gamma/models/smoke")
val sysmlSmoke = isolated.rootProject.projectDirectory.dir("subprojects/frontends/sysml/models/smoke")
val theta = artifactsOf("theta-xsts-cli", ":theta-executor")

dependencies {
    implementation(libs.bundles.ktor.server)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.clikt)
    implementation(libs.clikt.core)
    implementation(libs.ktor.http)
    implementation(libs.ktor.serialization)
    implementation(libs.ktor.utils)
    implementation(libs.ktor.websockets)
    implementation(libs.micrometer.core)

    implementation(project(":gamma.lang"))
    implementation(project(":lang-ide-common"))
    implementation(project(":logging"))
    implementation(project(":oxsts.lang.ide"))
    implementation(project(":gamma.lang.ide"))
    implementation(project(":portfolios"))

    testFixturesApi(project(":lang-ide-common"))
    testFixturesApi(libs.kotlinx.coroutines.core)
    testFixturesApi(libs.kotlinx.serialization.json)
    testFixturesApi(libs.bundles.ktor.server)
    testFixturesApi(libs.bundles.ktor.client.test)
    testFixturesApi(libs.ktor.client.core)
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.junit.api)
    testFixturesImplementation(libs.ktor.http)
    testFixturesImplementation(libs.ktor.serialization)
    testFixturesImplementation(libs.ktor.utils)
    testFixturesImplementation(libs.ktor.websockets)

    testImplementation(testFixtures(project(":verifier")))
    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    applicationName = "semantifyr-live-backend"
    mainClass = "hu.bme.mit.semantifyr.live.backend.SemantifyrLiveCliKt"
}

val generateBuildInfo = tasks.register<WriteProperties>("generateBuildInfo") {
    destinationFile = layout.buildDirectory.file("generated/buildinfo/build-info.properties")
    property("commit", commitHash())
    outputs.cacheIf {
        true
    }
}

tasks.jar {
    from(generateBuildInfo)
}

val semanticLibrariesDirectory = layout.buildDirectory.dir("staging/semantic-libraries")
val webDirectory = layout.buildDirectory.dir("staging/web")

val cloneSemanticLibraries = tasks.register<Sync>("cloneSemanticLibraries") {
    from(sysmlLibrary) {
        into("sysml")
    }
    from(gammaLibrary) {
        into("gamma")
    }
    into(semanticLibrariesDirectory)
}

val cloneWebDistributions = tasks.register<Sync>("cloneWebDistributions") {
    from(frontendDistribution)
    from(sysmlWorker) {
        exclude("sysml.tmLanguage.json")
        into("sysml-worker")
    }
    into(webDirectory)
}

fun JavaExec.servesLive() {
    runsWithTheta()
    inputs.files(cloneSemanticLibraries).withPathSensitivity(PathSensitivity.RELATIVE)

    classpath = files(tasks.jar, configurations.runtimeClasspath)
    args("start")

    environment("SEMANTIFYR_LIVE_PORT", "18080")
    environment("SEMANTIFYR_LIVE_METRICS_PORT", "18081")
    val semanticLibraries = semanticLibrariesDirectory
    val workDirectory = layout.buildDirectory.dir("work")
    doFirst {
        environment("SEMANTIFYR_LIVE_SEMANTIC_LIBRARIES_DIR", semanticLibraries.get().asFile.absolutePath)
        environment("SEMANTIFYR_LIVE_ROOT_WORK_DIR", workDirectory.get().asFile.absolutePath)
    }
}

tasks.named<JavaExec>("run") {
    servesLive()
    inputs.files(cloneWebDistributions).withPathSensitivity(PathSensitivity.RELATIVE)

    val web = webDirectory
    doFirst {
        environment("SEMANTIFYR_LIVE_WEB_ROOT_DIR", web.get().asFile.absolutePath)
    }
}

tasks.register<JavaExec>("runDev") {
    group = ApplicationPlugin.APPLICATION_GROUP

    servesLive()

    mainClass = application.mainClass
}

fun Test.readsPath(
    property: String,
    path: FileCollection,
) {
    inputs.files(path).withPathSensitivity(PathSensitivity.RELATIVE)
    val module = layout.projectDirectory.asFile
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-D$property=${path.singleFile.relativeTo(module).invariantSeparatorsPath}")
    }
}

fun FileCollection.named(name: String): FileCollection {
    return filter {
        it.name == name
    }
}

testing {
    suites {
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project(":logging"))
                implementation(project(":oxsts.lang.ide"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.http)
                implementation(libs.ktor.serialization)
                implementation(libs.ktor.utils)
                implementation(libs.ktor.websockets)
                implementation(libs.micrometer.core)
            }
        }
    }
}

tasks.named<Test>("integrationTest") {
    readsPath("semantifyr.live.semanticLibraries", files(cloneSemanticLibraries))
    readsPath("semantifyr.live.gammaCompiledModel", gammaCompiledSmoke.named("smoke.semantifyr"))
    readsPath("semantifyr.live.sysmlCompiledModel", sysmlCompiledSmoke.named("smoke.semantifyr"))
    inputs.file(isolated.rootProject.projectDirectory.file("oxsts-test-models/smoke/smoke.semantifyr")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(gammaSmoke.file("smoke.gamma"), gammaSmoke.file("manifest.json"), sysmlSmoke.file("manifest.json")).withPathSensitivity(PathSensitivity.RELATIVE)
}

val liveImageContext = tasks.register<Tar>("liveImageContext") {
    archiveFileName = "live-image-context.tar.gz"
    compression = Compression.GZIP
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
