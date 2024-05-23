/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `java-library`
    `java-test-fixtures`
    jacoco
    java
    id("hu.bme.mit.semantifyr.gradle.eclipse")
}

repositories {
    mavenCentral()
}

val libs = the<LibrariesForLibs>()

dependencies {
    testFixturesApi(libs.junit.api)
    testFixturesApi(libs.junit.params)
    testFixturesApi(libs.mockito.core)
    testFixturesApi(libs.mockito.junit)

    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
    test {
        useJUnitPlatform {
            excludeTags("slow")
        }

        minHeapSize = "512m"
        maxHeapSize = "4G"

        finalizedBy(tasks.jacocoTestReport)
    }

    jacocoTestReport {
        dependsOn(tasks.test)
        reports {
            xml.required.set(true)
        }
    }

    jar {
        manifest {
            attributes(
                "Bundle-SymbolicName" to "${project.group}.${project.name}",
                "Bundle-Version" to project.version,
            )
        }
    }

    val generateEclipseSourceFolders by tasks.registering

    register("prepareEclipse") {
        dependsOn(generateEclipseSourceFolders)
        dependsOn(tasks.named("eclipseJdt"))
    }

    eclipseClasspath {
        dependsOn(generateEclipseSourceFolders)
    }
}
