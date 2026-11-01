/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.registerVerificationTestService

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
    id("hu.bme.mit.semantifyr.gradle.conventions.integration")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":portfolios"))
    api(libs.kotlinx.serialization.json)

    implementation(project(":logging"))
    implementation(project(":sysml-frontend"))
    implementation(project(":sysml-wrapper"))
    implementation(testFixtures(project(":sysml-frontend")))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project(":verifier")))
    testImplementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass = "hu.bme.mit.semantifyr.benchmark.BenchmarkMainKt"
    applicationName = "semantifyr-benchmark"
}

val rootDirectory = isolated.rootProject.projectDirectory

tasks.register<JavaExec>("campaign") {
    group = "benchmark"
    description = "Runs or resumes the campaign named by -Pcampaign, analyses its results and compares them with its reference."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    outputs.upToDateWhen { false }
    usesService(gradle.sharedServices.registerVerificationTestService())
    inputs.files(tasks.named("cloneTheta"))

    val campaign = providers.gradleProperty("campaign")
    val fresh = providers.gradleProperty("benchmark.fresh").map { it.toBoolean() }.orElse(false)
    val baseline = providers.gradleProperty("benchmark.baseline").map { rootDirectory.dir(it).asFile.absolutePath }
    val timeTolerance = providers.gradleProperty("benchmark.timeTolerance")
    val runexec = providers.gradleProperty("benchmark.runexec").map { it.toBoolean() }.orElse(true)
    val runexecOptions = providers.gradleProperty("benchmark.runexecOptions")
    val outputDirectory = layout.buildDirectory.dir("benchmark").map { it.asFile.absolutePath }
    val referenceDirectory = layout.projectDirectory.dir("reference").asFile.absolutePath
    val sysmlExamples = rootDirectory.dir("subprojects/frontends/sysml/models/examples").asFile.absolutePath
    val oxstsModels = rootDirectory.dir("oxsts-test-models").asFile.absolutePath
    argumentProviders.add(
        CommandLineArgumentProvider {
            val name = campaign.orNull ?: throw GradleException("Name the campaign with -Pcampaign=<name>")
            listOf("run", name, "--output", outputDirectory.get(), "--reference", referenceDirectory, "--sysml-examples", sysmlExamples, "--oxsts-models", oxstsModels) +
                listOfNotNull("--fresh".takeIf { fresh.get() }) +
                (baseline.orNull?.let { listOf("--baseline", it) } ?: emptyList()) +
                (timeTolerance.orNull?.let { listOf("--time-tolerance", it) } ?: emptyList()) +
                listOfNotNull("--no-runexec".takeUnless { runexec.get() }) +
                (runexecOptions.orNull?.let { listOf("--runexec-options", it) } ?: emptyList())
        },
    )

    val thetaCliDirectory = tasks.named<Sync>("cloneTheta").map { it.destinationDir.absolutePath }
    doFirst {
        environment("PATH", "${thetaCliDirectory.get()}${File.pathSeparator}${System.getenv("PATH")}")
    }
}

tasks.register<JavaExec>("campaignReference") {
    group = "benchmark"
    description = "Rewrites the committed reference results and analysis of the campaign named by -Pcampaign from its latest results."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    outputs.upToDateWhen { false }

    val campaign = providers.gradleProperty("campaign")
    val outputDirectory = layout.buildDirectory.dir("benchmark").map { it.asFile.absolutePath }
    val referenceDirectory = layout.projectDirectory.dir("reference").asFile.absolutePath
    argumentProviders.add(
        CommandLineArgumentProvider {
            val name = campaign.orNull ?: throw GradleException("Name the campaign with -Pcampaign=<name>")
            listOf("reference", name, "--output", outputDirectory.get(), "--reference", referenceDirectory)
        },
    )
}

tasks.register<JavaExec>("campaignAnalysis") {
    group = "benchmark"
    description = "Analyses the results of the campaign named by -Pcampaign, or of the directory -Pbenchmark.results, into -Pbenchmark.analysis (default: analysis/ next to the results)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    outputs.upToDateWhen { false }

    val campaign = providers.gradleProperty("campaign")
    val results = providers.gradleProperty("benchmark.results").map { rootDirectory.dir(it).asFile.absolutePath }
    val analysis = providers.gradleProperty("benchmark.analysis").map { rootDirectory.dir(it).asFile.absolutePath }
    val outputDirectory = layout.buildDirectory.dir("benchmark").map { it.asFile.absolutePath }
    argumentProviders.add(
        CommandLineArgumentProvider {
            val resultsDirectory = results.orNull
                ?: campaign.orNull?.let { "${outputDirectory.get()}/$it" }
                ?: throw GradleException("Name the campaign with -Pcampaign=<name> or its results with -Pbenchmark.results=<directory>")
            listOf("analyze", resultsDirectory, analysis.orNull ?: "$resultsDirectory/analysis")
        },
    )
}

distributions.main {
    contents {
        from(rootDirectory.dir("subprojects/frontends/sysml/models/examples")) {
            into("models/sysml")
        }
        from(rootDirectory.dir("oxsts-test-models/semantic")) {
            into("models/semantic")
        }
        from(rootDirectory.dir("oxsts-test-models/smoke")) {
            into("models/smoke")
        }
        from(layout.projectDirectory.file("src/main/kotlin/hu/bme/mit/semantifyr/benchmark/campaigns/Campaigns.kt")) {
            into("campaigns")
        }
        from(layout.projectDirectory.dir("reference")) {
            into("reference")
        }
        // cmd.exe would reject the Windows launcher's expanded classpath, as it did the CLI's
        exclude("**/*.bat")
        val version = project.version.toString()
        filesMatching("README.md") {
            filter { it.replace("@VERSION@", version) }
        }
    }
}

tasks.distTar {
    enabled = false
}
