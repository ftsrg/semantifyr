/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.xtext")
}

dependencies {
    api(project(":oxsts.model"))
}

xtext {
    mweFile = layout.projectDirectory.file("src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2")
    xtextFile = layout.projectDirectory.file("src/main/java/hu/bme/mit/semantifyr/oxsts/lang/Oxsts.xtext")
}

val syncModel by tasks.registering(Sync::class) {
    from("../oxsts.model/model")
    into("model")
}

val generateXtextLanguage by tasks.getting(JavaExec::class) {
    inputs.files(syncModel.get().outputs)
}
