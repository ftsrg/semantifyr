plugins {
    id("hu.bme.mit.gamma.gradle.conventions.application")
    kotlin("jvm") version "1.9.10"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":hu.bme.mit.gamma.oxsts.lang"))
    implementation(project(":hu.bme.mit.gamma.oxsts.model"))

    implementation(libs.kotlinx.cli)

    api(platform(libs.xtext.bom))
    api(libs.ecore)
    api(libs.xtext.core)
    api(libs.xtext.xbase)
}

tasks.startScripts {
    classpath = files("%APP_HOME%/lib/*")
}
