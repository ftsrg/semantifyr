/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import org.junit.jupiter.api.Assumptions

private val requiredBackends = System.getProperty("semantifyr.tests.requiredBackends").orEmpty()
    .split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .toSet()

fun assumeBackendAvailable(key: ExecutorKey<*>) {
    val report = ExecutionEnvironment.Empty.availability(key) as? AvailabilityReport.Unavailable ?: return
    check(key.name !in requiredBackends) {
        "${key.name} is required on this host: ${report.reason}"
    }
    Assumptions.abort<Unit>("${report.reason}, skipping")
}
