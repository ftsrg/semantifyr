/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.xtext")
}

xtext {
    mweFile = "src/main/java/hu/bme/mit/semantifyr/frontends/gamma/lang/GenerateGamma.mwe2"
    xtextFile = "src/main/java/hu/bme/mit/semantifyr/frontends/gamma/lang/Gamma.xtext"
}
