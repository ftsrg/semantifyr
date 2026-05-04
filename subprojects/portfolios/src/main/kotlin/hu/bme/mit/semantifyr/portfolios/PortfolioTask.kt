/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendConfig
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment

class PortfolioTask<T : BackendConfig>(
    val backend: VerificationBackend<T>,
    val config: T,
    val subPath: String,
) {
    fun isAvailable(environment: ExecutionEnvironment): Boolean {
        return environment.availability(backend.executorKey).isUsable
    }

    suspend fun run(
        parentInjector: Injector,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        return backend.verify(parentInjector, config, request, environment)
    }
}
