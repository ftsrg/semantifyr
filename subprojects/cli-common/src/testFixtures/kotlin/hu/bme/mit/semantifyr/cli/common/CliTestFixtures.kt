/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.fakeMetadata
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio

fun fakePortfolio(id: String, vararg verdicts: VerificationVerdict): SingleBackendPortfolio<FakeConfig> {
    val remaining = ArrayDeque(verdicts.toList())
    val backend = FakeBackend(id) {
        BackendVerificationResult(verdict = remaining.removeFirst(), metadata = fakeMetadata(id))
    }
    return SingleBackendPortfolio(backend, FakeConfig(id))
}

inline fun <reified T> jsonLines(output: String): List<T> {
    return output.lines().filter {
        it.isNotBlank()
    }.map {
        jsonLineFormat.decodeFromString<T>(it)
    }
}
