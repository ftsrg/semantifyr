/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.xtext")
}

dependencies {
    api(libs.antlr.runtime)
    api(libs.ecore)
    api(libs.emf.common)
    api(libs.guice)
    api(libs.xtext.util)
}

xtext {
    mweFile = "src/main/java/hu/bme/mit/semantifyr/cex/lang/GenerateCex.mwe2"
    xtextFile = "src/main/java/hu/bme/mit/semantifyr/cex/lang/Cex.xtext"
}
