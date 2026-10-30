/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry

abstract class NpmInstallService : BuildService<BuildServiceParameters.None>

internal const val NPM_INSTALL_SERVICE_NAME = "npmInstallService"

fun BuildServiceRegistry.registerNpmInstallService(): Provider<NpmInstallService> {
    return registerIfAbsent(NPM_INSTALL_SERVICE_NAME, NpmInstallService::class.java) {
        maxParallelUsages.set(1)
    }
}
