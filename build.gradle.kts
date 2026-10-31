/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.dokka)
    `jacoco-report-aggregation`
}

val javadocModules = listOf("oxsts.lang", "oxsts.model")

// build-logic's semantifyrArtifact, which the root project does not load
fun semantifyrArtifact(kind: String): Action<AttributeContainer> {
    return Action {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Attribute.of("hu.bme.mit.semantifyr.artifact", String::class.java), kind)
    }
}

val javadoc = configurations.dependencyScope("javadoc")
val javadocFiles = configurations.resolvable("javadocFiles") {
    extendsFrom(javadoc.get())
    attributes(semantifyrArtifact("javadoc"))
}

val apiDocsDirectory = layout.buildDirectory.dir("api")

dependencies {
    subprojects.forEach { subproject ->
        subproject.plugins.withId("hu.bme.mit.semantifyr.gradle.conventions.jvm") {
            jacocoAggregation(project(subproject.path))
        }
        subproject.plugins.withId("org.jetbrains.kotlin.jvm") {
            dokka(project(subproject.path))
        }
    }
    javadocModules.forEach {
        javadoc(project(":$it"))
    }
}

dokka {
    moduleName = "Semantifyr"
}

val apiDocs = tasks.register<Sync>("apiDocs") {
    group = "documentation"

    from(tasks.named("dokkaGeneratePublicationHtml")) {
        into("kotlin")
    }
    from(javadocFiles) {
        into("java")
    }
    into(apiDocsDirectory)

    val links = listOf("kotlin" to "Kotlin modules") + javadocModules.map { "java/$it" to it }
    val items = links.joinToString("\n") { (path, label) -> "<li><a href=\"$path/index.html\">$label</a></li>" }
    val index = "<!doctype html>\n<html lang=\"en\">\n<meta charset=\"utf-8\">\n<title>Semantifyr API</title>\n<h1>Semantifyr API</h1>\n<ul>\n$items\n</ul>\n"
    val indexFile = apiDocsDirectory.map { it.file("index.html").asFile }
    inputs.property("index", index)
    doLast {
        indexFile.get().writeText(index)
    }
}

configurations.consumable("apiDocsElements") {
    attributes(semantifyrArtifact("api-docs"))
    outgoing.artifact(apiDocsDirectory) {
        builtBy(apiDocs)
    }
}

val suiteReportTasks = reporting.reports.run {
    listOf(
        register<JacocoCoverageReport>("mergedJacocoTestReport") { testSuiteName = "test" },
        register<JacocoCoverageReport>("mergedJacocoVerificationTestReport") { testSuiteName = "verificationTest" },
        register<JacocoCoverageReport>("mergedJacocoIntegrationTestReport") { testSuiteName = "integrationTest" },
    ).map { report ->
        report.flatMap { it.reportTask }
    }
}

tasks.register<JacocoReport>("mergedJacocoReport") {
    group = "verification"

    suiteReportTasks.forEach { reportTask ->
        executionData.from(reportTask.map { it.executionData })
        sourceDirectories.from(reportTask.map { it.sourceDirectories })
        classDirectories.from(reportTask.map { it.classDirectories })
    }

    reports {
        xml.required = true
        html.required = true
    }
}

val generatedSourceExclusions = "**/src-gen/**,**/generated/**,**/xtext-gen/**,**/emf-gen/**"

sonar {
    properties {
        property("sonar.coverage.exclusions", generatedSourceExclusions)
        property("sonar.exclusions", generatedSourceExclusions)
        property("sonar.projectKey", "ftsrg_semantifyr")
        property("sonar.organization", "ftsrg-github")
        property("sonar.projectName", "Semantifyr")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "UTF-8")
    }
}
