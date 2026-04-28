/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verification.portfolio.BackendExecutor
import kotlinx.coroutines.delay
import org.mockito.kotlin.mock
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

fun fakeCase(qualifiedName: String = "pkg.Foo", vararg tags: String): VerificationCase {
    return VerificationCase(
        classDeclaration = mock<ClassDeclaration>(),
        qualifiedName = qualifiedName,
        tags = tags.toSet(),
    )
}

val noopExecutor: BackendExecutor = object : BackendExecutor {
    override suspend fun <T> withPermit(block: suspend () -> T): T {
        return block()
    }
}

fun fakeRequest(case: VerificationCase = fakeCase()): VerificationRequest {
    return VerificationRequest(
        case = case,
        input = mock<InlinedOxsts>(),
        compilation = mock<FlattenedCompilationContext>(),
        artifactOutputPath = Paths.get(System.getProperty("java.io.tmpdir")),
    )
}

fun fakeMetadata(backendId: String = "test", caseQualifiedName: String = "pkg.Foo"): VerificationRunMetadata {
    return VerificationRunMetadata(
        backendId = backendId,
        startedAt = Clock.System.now(),
        caseQualifiedName = caseQualifiedName,
    )
}

class FakeBackend(
    override val id: String,
    private val behavior: suspend () -> BackendVerificationResult,
) : VerificationBackend<Unit>() {
    val invocations = AtomicInteger(0)
    val cancellations = AtomicInteger(0)

    override suspend fun verify(
        config: Unit,
        request: VerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
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
                BackendVerificationResult(verdict = verdict, metadata = fakeMetadata(id))
            }
        }

        fun delayed(
            id: String,
            delayMillis: Long,
            verdict: VerificationVerdict,
        ): FakeBackend {
            return FakeBackend(id) {
                delay(delayMillis)
                BackendVerificationResult(verdict = verdict, metadata = fakeMetadata(id))
            }
        }

        fun throwing(id: String, throwable: Throwable): FakeBackend {
            return FakeBackend(id) {
                throw throwable
            }
        }
    }
}
