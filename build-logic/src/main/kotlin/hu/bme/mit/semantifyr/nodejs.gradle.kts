/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import com.github.gradle.node.npm.proxy.ProxySettings

plugins {
    base
    id("com.github.node-gradle.node")
    id("hu.bme.mit.semantifyr.formatting")
}

node {
    version = providers.fileContents(isolated.rootProject.projectDirectory.file(".nvmrc")).asText.get().trim()
    download = true
    fastNpmInstall = true
    distBaseUrl = null
    // CodeQL's autobuild sets empty proxy system properties, which the plugin turns into HTTPS_PROXY=http://:
    nodeProxySettings = ProxySettings.OFF
}

if (isolated != isolated.rootProject) {
    tasks.npmInstall {
        enabled = false
    }
}
