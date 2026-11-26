/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.backend")
    kotlin("jvm")
}

dependencies {
    api(project(":backend"))
    api(project(":oxsts.lang"))
    api(project(":oxsts.model"))
    api(project(":spin-executor"))
    api(project(":utils"))
    api(libs.guice)
    api(libs.guice.extensions.assistedinject)

    implementation(project(":compiler"))
    implementation(project(":guice-common"))
    implementation(project(":logging"))
    implementation(libs.ecore)
    implementation(libs.emf.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    implementation(libs.xtext.core)

    testImplementation(testFixtures(project(":backend")))
    testImplementation(testFixtures(project(":oxsts.lang")))
}

testing {
    suites {
        named<JvmTestSuite>("adapterTest") {
            dependencies {
                implementation(project(":compiler"))
                implementation(libs.guice)
            }
        }

        named<JvmTestSuite>("nightlyTest") {
            dependencies {
                implementation(project(":compiler"))
                implementation(libs.guice)
            }
        }

        named<JvmTestSuite>("semanticTest") {
            dependencies {
                implementation(project(":compiler"))
                implementation(libs.guice)
            }
        }
    }
}
