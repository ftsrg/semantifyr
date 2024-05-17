plugins {
    id("hu.bme.mit.gamma.gradle.conventions.application")
    kotlin("jvm") version "1.9.10"
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    inputs.dir("Test Models")
}

repositories {
    mavenCentral()
    maven("https://repo.eclipse.org/content/groups/viatra/")
}

dependencies {
    implementation(project(":hu.bme.mit.gamma.oxsts.lang"))
    implementation(project(":hu.bme.mit.gamma.oxsts.model"))

    implementation("com.google.inject:guice:7.0.0")
    implementation(libs.kotlinx.cli)
    implementation(libs.ecore.codegen)
    implementation(libs.viatra.query.language) {
        exclude("com.google.inject", "guice")
    }
    implementation(libs.viatra.query.runtime)
    implementation(libs.viatra.transformation.runtime)

    testFixturesApi("commons-io:commons-io:2.14.0")
    testFixturesApi(project(":hu.bme.mit.gamma.oxsts.lang"))
    testFixturesApi(testFixtures(project(":hu.bme.mit.gamma.oxsts.lang")))
}
