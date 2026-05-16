/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    base
    id("com.github.node-gradle.node")
}

node {
    version = "22.14.0"
    download = true
}
