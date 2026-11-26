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
    api(project(":nuxmv-executor"))
    api(project(":oxsts.lang"))
    api(project(":oxsts.model"))
    api(project(":utils"))
    api(libs.ecore)
    api(libs.guice)
    api(libs.guice.extensions.assistedinject)

    implementation(project(":compiler"))
    implementation(project(":guice-common"))
    implementation(project(":logging"))
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
                implementation(project(":oxsts.lang"))
                implementation(libs.guice)
            }
        }

        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project(":oxsts.lang"))
                implementation(libs.guice)
            }
        }

        named<JvmTestSuite>("nightlyTest") {
            dependencies {
                implementation(project(":compiler"))
                implementation(project(":oxsts.lang"))
                implementation(libs.guice)
            }
        }

        named<JvmTestSuite>("semanticTest") {
            dependencies {
                implementation(project(":compiler"))
                implementation(project(":oxsts.lang"))
                implementation(libs.guice)
            }
        }
    }
}
