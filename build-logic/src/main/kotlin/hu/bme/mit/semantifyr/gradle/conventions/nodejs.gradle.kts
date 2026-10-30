/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import com.github.gradle.node.npm.proxy.ProxySettings

plugins {
    base
    id("com.github.node-gradle.node")
}

node {
    version = "22.23.4"
    download = true
    // the Node.js repository is declared in settings, which forbids project repositories
    distBaseUrl = null
    // CodeQL's autobuild sets empty proxy system properties, which the plugin turns into HTTPS_PROXY=http://:
    nodeProxySettings = ProxySettings.OFF
}

val npmInstallServiceProvider = gradle.sharedServices.registerNpmInstallService()

// the npm modules share one workspace root, and parallel installs into it corrupt node_modules
tasks.npmInstall {
    usesService(npmInstallServiceProvider)
}
