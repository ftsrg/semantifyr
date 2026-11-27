/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.xtext")
}

dependencies {
    api(libs.guava)

    implementation(libs.reload4j)
    implementation(libs.xtext.xbase.lib)
}

xtext {
    mweFile = "src/main/java/hu/bme/mit/semantifyr/xsts/lang/GenerateXsts.mwe2"
    xtextFile = "src/main/java/hu/bme/mit/semantifyr/xsts/lang/Xsts.xtext"
}
