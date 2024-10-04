/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.xtext-generated")
}

dependencies {
    api(project(":oxsts.model"))

    implementation(platform(libs.xtext.bom))
    implementation(libs.xtext.core)
    implementation(libs.xtext.xbase)

    testFixturesApi(libs.xtext.testing)

    mwe2(libs.xtext.generator)
    mwe2(libs.xtext.generator.antlr)
}

val syncModel by tasks.registering(Sync::class) {
    from("../oxsts.model/model")
    into("model")
}

val generateXtextLanguage by tasks.registering(JavaExec::class) {
    mainClass.set("org.eclipse.emf.mwe2.launch.runtime.Mwe2Launcher")
    classpath(configurations.mwe2)

    inputs.files(syncModel.get().outputs)

    inputs.file("src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2")
    inputs.file("src/main/java/hu/bme/mit/semantifyr/oxsts/lang/Oxsts.xtext")

    outputs.dir("src/main/xtext-gen")
    outputs.dir("src/testFixtures/xtext-gen")
    outputs.dir(project(":oxsts.lang.ide").layout.projectDirectory.dir("src/main/xtext-gen"))
    outputs.dir(project(":oxsts.lang.ide").layout.projectDirectory.dir("src/main/java"))

    args("src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2", "-p", "rootPath=/$projectDir/..")
}

tasks {
    jar {
        from(sourceSets.main.map { it.allSource }) {
            include("**/*.xtext")
        }
    }

    listOf("compileJava", "processResources", "compileTestJava", "compileTestFixturesJava", "processTestFixturesResources").forEach { task ->
        named(task) {
            inputs.files(generateXtextLanguage.get().outputs)
        }
    }

    clean {
        delete("src/main/xtext-gen")
        delete("src/test/java")
        delete("src/testFixtures/xtext-gen")
    }
}
