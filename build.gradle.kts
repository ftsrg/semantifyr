/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.eclipse")
}

tasks.wrapper {
    version = "8.8"
    distributionType = Wrapper.DistributionType.ALL
}
