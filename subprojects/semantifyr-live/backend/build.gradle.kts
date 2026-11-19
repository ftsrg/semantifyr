/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.commitHash
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact
import java.time.Instant

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.conventions.suites")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val frontendDistribution = configurations.dependencyScope("frontendDistribution")
val frontendDistributionFiles = configurations.resolvable("frontendDistributionFiles") {
    extendsFrom(frontendDistribution.get())
    attributes(semantifyrArtifact("web-distribution"))
}

val gammaCompiledExamples = configurations.dependencyScope("gammaCompiledExamples")
val gammaCompiledExamplesFiles = configurations.resolvable("gammaCompiledExamplesFiles") {
    extendsFrom(gammaCompiledExamples.get())
    attributes(semantifyrArtifact("compiled-examples"))
}

val sysmlCompiledExamples = configurations.dependencyScope("sysmlCompiledExamples")
val sysmlCompiledExamplesFiles = configurations.resolvable("sysmlCompiledExamplesFiles") {
    extendsFrom(sysmlCompiledExamples.get())
    attributes(semantifyrArtifact("compiled-examples"))
}

val sysmlWorker = configurations.dependencyScope("sysmlWorker")
val sysmlWorkerFiles = configurations.resolvable("sysmlWorkerFiles") {
    extendsFrom(sysmlWorker.get())
    attributes(semantifyrArtifact("sysml-worker"))
}

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

    frontendDistribution(project(":semantifyr-live-frontend"))

    gammaCompiledExamples(project(":gamma-frontend"))
    sysmlCompiledExamples(project(":sysml-wrapper"))
    sysmlWorker(project(":sysml-wrapper"))
}

application {
    applicationName = "semantifyr-live-backend"
    mainClass = "hu.bme.mit.semantifyr.live.backend.SemantifyrLiveCliKt"
}

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outputDir = layout.buildDirectory.dir("generated/buildinfo")
    val version = project.version.toString()
    val commitHash = commitHash()
    inputs.property("version", version)
    inputs.property("commitHash", commitHash)
    outputs.dir(outputDir)

    doLast {
        val buildTime = Instant.now().toString()

        outputDir.get().asFile.mkdirs()
        outputDir.get().file("build-info.properties").asFile.writeText(
            "version=$version\ncommit=${commitHash.get()}\nbuildTime=$buildTime\n",
        )
    }
}

sourceSets.main {
    resources.srcDir(generateBuildInfo.map { it.outputs.files.singleFile })
}

val cloneSemanticLibraries = tasks.register<Sync>("cloneSemanticLibraries") {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/libraries/default")) {
        into("sysmlv2")
    }
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/libraries/default")) {
        into("gamma")
    }
    into(layout.buildDirectory.dir("staging/semantic-libraries"))
}

val cloneGammaTestModels = tasks.register<Sync>("cloneGammaTestModels") {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/examples"))
    into(layout.buildDirectory.dir("staging/gamma-test-models"))
}

val cloneOxstsTestModels = tasks.register<Sync>("cloneOxstsTestModels") {
    from(rootProject.layout.projectDirectory.dir("oxsts-test-models/smoke"))
    into(layout.buildDirectory.dir("staging/oxsts-test-models"))
}

val cloneGammaLibraryModels = tasks.register<Sync>("cloneGammaLibraryModels") {
    from(gammaCompiledExamplesFiles)
    into(layout.buildDirectory.dir("staging/gamma-library-models"))
}

val cloneSysmlLibraryModels = tasks.register<Sync>("cloneSysmlLibraryModels") {
    from(sysmlCompiledExamplesFiles)
    into(layout.buildDirectory.dir("staging/sysml-library-models"))
}

val cloneWebDistributions = tasks.register<Sync>("cloneWebDistributions") {
    from(frontendDistributionFiles)
    from(sysmlWorkerFiles) {
        exclude("sysml.tmLanguage.json")
        into("sysml-worker")
    }
    into(layout.buildDirectory.dir("staging/web"))
}

// the Theta the image ships and a local run puts on its path
val theta = configurations.dependencyScope("theta")
val thetaFiles = configurations.resolvable("thetaFiles") {
    extendsFrom(theta.get())
    attributes(semantifyrArtifact("theta-xsts-cli"))
}

dependencies {
    theta(project(":theta-executor"))
}

val cloneShippedTheta = tasks.register<Sync>("cloneShippedTheta") {
    from(thetaFiles)
    into(layout.buildDirectory.dir("shipped-theta-xsts-cli"))
}

tasks.named<JavaExec>("run") {
    group = "application"

    inputs.files(cloneSemanticLibraries)
    inputs.files(cloneWebDistributions)
    inputs.files(cloneShippedTheta)

    val thetaCliDir = cloneShippedTheta.map { it.destinationDir.absolutePath }

    val stagingDir = layout.buildDirectory.dir("staging").get().asFile
    val workDir = layout.buildDirectory.dir("work").get().asFile

    args = listOf("start")

    environment("SEMANTIFYR_LIVE_SEMANTIC_LIBRARIES_DIR", stagingDir.resolve("semantic-libraries").absolutePath)
    environment("SEMANTIFYR_LIVE_WEB_ROOT_DIR", stagingDir.resolve("web").absolutePath)
    environment("SEMANTIFYR_LIVE_PORT", "18080")
    environment("SEMANTIFYR_LIVE_METRICS_PORT", "18081")
    environment("SEMANTIFYR_LIVE_ROOT_WORK_DIR", workDir.absolutePath)

    doFirst {
        environment("PATH", "${thetaCliDir.get()}${File.pathSeparator}${System.getenv("PATH")}")
    }
}

tasks.register<JavaExec>("runDev") {
    group = "application"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass = application.mainClass
    jvmArgs(application.applicationDefaultJvmArgs)

    inputs.files(cloneSemanticLibraries)
    inputs.files(cloneShippedTheta)

    val thetaCliDir = cloneShippedTheta.map { it.destinationDir.absolutePath }

    val stagingDir = layout.buildDirectory.dir("staging").get().asFile
    val workDir = layout.buildDirectory.dir("work").get().asFile

    args = listOf("start")

    environment("SEMANTIFYR_LIVE_SEMANTIC_LIBRARIES_DIR", stagingDir.resolve("semantic-libraries").absolutePath)
    environment("SEMANTIFYR_LIVE_PORT", "18080")
    environment("SEMANTIFYR_LIVE_METRICS_PORT", "18081")
    environment("SEMANTIFYR_LIVE_ROOT_WORK_DIR", workDir.absolutePath)

    doFirst {
        environment("PATH", "${thetaCliDir.get()}${File.pathSeparator}${System.getenv("PATH")}")
    }
}

val stagingDirectory = "build/staging"

tasks.test {
    inputs.files(cloneSemanticLibraries)
    inputs.files(cloneGammaTestModels)

    systemProperty("semantifyr.live.semanticLibraries", "$stagingDirectory/semantic-libraries")
    systemProperty("semantifyr.live.gammaTestModels", "$stagingDirectory/gamma-test-models")
}

testing {
    suites {
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project(":logging"))
                implementation(project(":oxsts.lang.ide"))
            }

            targets.all {
                testTask.configure {
                    inputs.files(cloneSemanticLibraries)
                    inputs.files(cloneGammaTestModels)
                    inputs.files(cloneOxstsTestModels)
                    inputs.files(cloneGammaLibraryModels)
                    inputs.files(cloneSysmlLibraryModels)

                    systemProperty("semantifyr.live.semanticLibraries", "$stagingDirectory/semantic-libraries")
                    systemProperty("semantifyr.live.gammaTestModels", "$stagingDirectory/gamma-test-models")
                    systemProperty("semantifyr.live.oxstsTestModels", "$stagingDirectory/oxsts-test-models")
                    systemProperty("semantifyr.live.gammaLibraryModels", "$stagingDirectory/gamma-library-models")
                    systemProperty("semantifyr.live.sysmlLibraryModels", "$stagingDirectory/sysml-library-models")
                }
            }
        }
    }
}

tasks.register("prepareDockerBuild") {
    inputs.files(cloneSemanticLibraries)
    inputs.files(cloneWebDistributions)
    inputs.files(cloneShippedTheta)
    inputs.files(tasks.installDist)
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
    from(cloneShippedTheta) {
        into("build/shipped-theta-xsts-cli")
    }
}

tasks.pack {
    dependsOn(liveImageContext)
}
