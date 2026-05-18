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

abstract class VerificationTestService : BuildService<BuildServiceParameters.None>

internal const val VERIFICATION_TEST_SERVICE_NAME = "verificationTestService"

fun BuildServiceRegistry.registerVerificationTestService(): Provider<VerificationTestService> {
    return registerIfAbsent(VERIFICATION_TEST_SERVICE_NAME, VerificationTestService::class.java) {
        maxParallelUsages.set(1)
    }
}
