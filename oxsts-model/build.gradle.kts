plugins {
    id("hu.bme.mit.gamma.gradle.conventions.generated")
    kotlin("jvm") version "1.9.10"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.ecore)
}
