/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.registerVerificationTestService

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
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

    runtimeOnly(libs.slf4j.log4j)

    testImplementation(testFixtures(project(":verifier")))
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.register<JavaExec>("campaign") {
    group = "benchmark"
    description = "Runs or resumes the campaign named by -Pcampaign, analyses its results and compares them with its reference."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "hu.bme.mit.semantifyr.benchmark.BenchmarkMainKt"
    outputs.upToDateWhen { false }
    usesService(gradle.sharedServices.registerVerificationTestService())
    inputs.files(tasks.named("cloneTheta"))

    val rootDirectory = isolated.rootProject.projectDirectory
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
    description = "Rewrites the committed reference results of the campaign named by -Pcampaign from its latest results."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "hu.bme.mit.semantifyr.benchmark.BenchmarkMainKt"
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
