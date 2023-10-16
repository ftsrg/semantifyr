package hu.bme.mit.gamma.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `java-library`
    `java-test-fixtures`
    jacoco
    java
    id("hu.bme.mit.gamma.gradle.eclipse")
}

repositories {
    mavenCentral()
}

val libs = the<LibrariesForLibs>()

dependencies {
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.junit.params)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit)
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
    test {
        useJUnitPlatform {
            excludeTags("slow")
        }
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
