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

abstract class BackendRunService : BuildService<BuildServiceParameters.None>

internal const val BACKEND_RUN_SERVICE_NAME = "backendRunService"

fun BuildServiceRegistry.registerBackendRunService(limit: Provider<Int>): Provider<BackendRunService> {
    return registerIfAbsent(BACKEND_RUN_SERVICE_NAME, BackendRunService::class.java) {
        maxParallelUsages.set(limit)
    }
}
