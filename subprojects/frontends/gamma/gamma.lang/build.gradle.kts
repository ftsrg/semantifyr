/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.xtext")
}

xtext {
    mweFile = layout.projectDirectory.file("src/main/java/hu/bme/mit/semantifyr/frontends/gamma/lang/GenerateGamma.mwe2")
    xtextFile = layout.projectDirectory.file("src/main/java/hu/bme/mit/semantifyr/frontends/gamma/lang/Gamma.xtext")
}
