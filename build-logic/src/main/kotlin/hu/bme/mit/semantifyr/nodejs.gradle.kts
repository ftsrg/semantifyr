/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import com.github.gradle.node.npm.proxy.ProxySettings
import hu.bme.mit.semantifyr.gradle.artifactsOf

plugins {
    base
    id("com.github.node-gradle.node")
    id("hu.bme.mit.semantifyr.formatting")
}

node {
    // .nvmrc is the one Node.js version: the workflows' set-up and the website read it too
    version = providers.fileContents(isolated.rootProject.projectDirectory.file(".nvmrc")).asText.get().trim()
    download = true
    // the Node.js repository is declared in settings, which forbids project repositories
    distBaseUrl = null
    // CodeQL's autobuild sets empty proxy system properties, which the plugin turns into HTTPS_PROXY=http://:
    nodeProxySettings = ProxySettings.OFF
}

// the root installs the one npm workspace, which the members' tasks read through readsWorkspace()
if (isolated != isolated.rootProject) {
    tasks.npmInstall {
        enabled = false
    }
    artifactsOf("npm-workspace", ":")
}
