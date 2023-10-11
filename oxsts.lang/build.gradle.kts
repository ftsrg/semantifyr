plugins {
    id("hu.bme.mit.gamma.gradle.conventions.generated")
    kotlin("jvm") version "1.9.10"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":oxsts-model"))

    api(platform(libs.xtext.bom))
    api(libs.xtext.core)
    api(libs.xtext.xbase)
}

