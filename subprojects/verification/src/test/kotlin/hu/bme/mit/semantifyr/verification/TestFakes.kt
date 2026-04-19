/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verification.portfolio.BackendExecutor
import kotlinx.coroutines.delay
import org.mockito.kotlin.mock
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * Test fakes shared by unit tests. Pure-JVM, no Guice / Theta / EMF wiring.
 * [fakeRequest] builds a [VerificationRequest] with a mock [InlinedOxsts] -- the portfolio
 * / strategy tests never inspect it.
 */

internal fun fakeCase(qualifiedName: String = "pkg.Foo", vararg tags: String): VerificationCase {
    return VerificationCase(
        classDeclaration = mock<ClassDeclaration>(),
        qualifiedName = qualifiedName,
        tags = tags.toSet(),
    )
}

internal val noopExecutor: BackendExecutor = object : BackendExecutor {
    override suspend fun <T> withPermit(block: suspend () -> T): T {
        return block()
    }
}

internal fun fakeRequest(case: VerificationCase = fakeCase()): VerificationRequest {
    return VerificationRequest(
        case = case,
        input = mock<InlinedOxsts>(),
        artifactOutputPath = Paths.get(System.getProperty("java.io.tmpdir")),
    )
}

internal fun fakeMetadata(backendId: String = "test", caseQualifiedName: String = "pkg.Foo"): VerificationRunMetadata {
    return VerificationRunMetadata(
        backendId = backendId,
        startedAt = Clock.System.now(),
        caseQualifiedName = caseQualifiedName,
    )
}

/**
 * Programmable [VerificationBackend] for portfolio tests. [behavior] decides what verdict
 * to return and how long to take. Tracks invocations and cancellations.
 *
 * Uses a sentinel [Unit] config -- these tests don't drive a real backend's config.
 */
internal class FakeBackend(
    override val id: String,
    private val behavior: suspend () -> VerificationResult,
) : VerificationBackend<Unit>() {
    val invocations = AtomicInteger(0)
    val cancellations = AtomicInteger(0)

    override suspend fun verify(
        config: Unit,
        request: VerificationRequest,
        environment: ExecutionEnvironment,
    ): VerificationResult {
        invocations.incrementAndGet()
        try {
            return behavior()
        } catch (c: CancellationException) {
            cancellations.incrementAndGet()
            throw c
        }
    }

    override fun probeAvailability(config: Unit, environment: ExecutionEnvironment): AvailabilityReport {
        return AvailabilityReport.Available
    }

    companion object {
        fun verdict(id: String, verdict: VerificationVerdict): FakeBackend {
            return FakeBackend(id) {
                VerificationResult(verdict = verdict, metadata = fakeMetadata(id))
            }
        }

        fun delayed(id: String, delayMillis: Long, verdict: VerificationVerdict): FakeBackend {
            return FakeBackend(id) {
                delay(delayMillis)
                VerificationResult(verdict = verdict, metadata = fakeMetadata(id))
            }
        }
    }
}
