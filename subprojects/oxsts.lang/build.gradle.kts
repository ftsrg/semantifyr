/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.xtext")
}

dependencies {
    api(project(":oxsts.model"))

    testRuntimeOnly(libs.slf4j.log4j)
}

xtext {
    mweFile = "src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2"
    xtextFile = "src/main/java/hu/bme/mit/semantifyr/oxsts/lang/Oxsts.xtext"
}

val syncModel = tasks.register<Sync>("syncModel") {
    from("../oxsts.model/model")
    into("model")
}

tasks.named("generateXtextLanguage") {
    inputs.files(syncModel)
}
