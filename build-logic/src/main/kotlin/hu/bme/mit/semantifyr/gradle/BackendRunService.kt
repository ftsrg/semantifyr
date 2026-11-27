/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.kotlin.dsl.assign

abstract class BackendRunService : BuildService<BuildServiceParameters.None>

fun BuildServiceRegistry.registerBackendRunService(limit: Provider<Int>): Provider<BackendRunService> {
    return registerIfAbsent("backendRunService", BackendRunService::class.java) {
        maxParallelUsages = limit
    }
}
