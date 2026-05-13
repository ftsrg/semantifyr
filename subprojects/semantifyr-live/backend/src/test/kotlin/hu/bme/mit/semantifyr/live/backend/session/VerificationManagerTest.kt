/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationStatus
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageClient
import hu.bme.mit.semantifyr.live.backend.lsp.service.VerificationsChangedParams
import hu.bme.mit.semantifyr.live.backend.lsp.session.LspSession
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationManager
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class VerificationManagerTest {

    private fun manager(concurrency: Int, timeout: Duration = 300.seconds): VerificationManager {
        val config = BackendConfig(
            verification = VerificationConfig(concurrency = concurrency, timeout = timeout),
        )
        return VerificationManager(config)
    }

    private fun fakeSession(scope: CoroutineScope): Pair<LspSession, SessionLanguageClient> {
        val client = mock<SessionLanguageClient>()
        val session = mock<LspSession> {
            on { this.sessionId } doReturn "session-1"
            on { this.coroutineScope } doReturn scope
            on { client() } doReturn client
        }
        return session to client
    }

    private fun verifyRequest(portfolio: String = "portfolio-1") =
        VerificationCaseRequest("file:///workspace/snippet.oxsts", LspWire.range(), portfolio)

    @Test
    fun `withPermit acquires and releases permit`() = runTest {
        val manager = manager(concurrency = 2)
        assertThat(manager.availablePermits).isEqualTo(2)
        manager.withPermit {
            assertThat(manager.availablePermits).isEqualTo(1)
        }
        assertThat(manager.availablePermits).isEqualTo(2)
    }

    @Test
    fun `withPermit releases permit on exception`() = runTest {
        val manager = manager(concurrency = 1)
        try {
            manager.withPermit {
                throw RuntimeException("boom")
            }
        } catch (_: RuntimeException) {
        }
        assertThat(manager.availablePermits).isEqualTo(1)
    }

    @Test
    fun `withPermit suspends when no permits available`() = runTest {
        val manager = manager(concurrency = 1)
        coroutineScope {
            var secondStarted = false
            val first = async {
                manager.withPermit {
                    assertThat(secondStarted).isFalse()
                }
            }
            val second = async {
                manager.withPermit {
                    secondStarted = true
                }
            }
            first.await()
            second.await()
            assertThat(secondStarted).isTrue()
            assertThat(manager.availablePermits).isEqualTo(1)
        }
    }

    @Test
    fun `run invokes the work lambda and returns its result`() = runTest {
        coroutineScope {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val (session, _) = fakeSession(scope)
            val manager = manager(concurrency = 1)

            val result = manager.run(session, verifyRequest(), VerificationKind.Verify) { "work-done" }.await()

            assertThat(result).isEqualTo("work-done")
        }
    }

    @Test
    suspend fun `run assigns a UUID-shaped verificationId and surfaces it via activeFor`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (session, _) = fakeSession(scope)
        val manager = manager(concurrency = 1)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val future = manager.run(session, verifyRequest(), VerificationKind.Verify) {
            started.complete(Unit)
            release.await()
            "done"
        }
        started.await()
        val active = manager.activeFor("session-1")
        release.complete(Unit)
        future.await()

        assertThat(active).hasSize(1)
        assertThat(active[0].verificationId).isNotBlank()
        assertThat(active[0].verificationId).isNotEqualTo("session-1")
    }

    @Test
    suspend fun `run notifies the client of active changes before and after work`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (session, client) = fakeSession(scope)
        val manager = manager(concurrency = 1)

        manager.run(session, verifyRequest(), VerificationKind.Verify) { "ok" }.await()

        val captor = argumentCaptor<VerificationsChangedParams>()
        verify(client, atLeastOnce()).verificationsChanged(captor.capture())
        // First notification carries the new entry. The last (after finally) carries the empty list.
        val first = captor.firstValue.active
        val last = captor.lastValue.active
        assertThat(first).hasSize(1)
        assertThat(first[0].verificationId).isNotBlank()
        assertThat(last).isEmpty()
    }

    @Test
    suspend fun `cancel cancels an in-flight verification and removes it from active`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (session, _) = fakeSession(scope)
        val manager = manager(concurrency = 1)
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()

        val future = manager.run(session, verifyRequest(), VerificationKind.Verify) {
            started.complete(Unit)
            never.await()
            "should not happen"
        }
        started.await()
        val verificationId = manager.activeFor("session-1").single().verificationId

        assertThat(manager.cancel(verificationId)).isTrue()
        awaitSilently(future)
        assertThat(manager.activeFor("session-1")).isEmpty()
    }

    @Test
    fun `cancel returns false for an unknown verificationId`() = runTest {
        val manager = manager(concurrency = 1)
        assertThat(manager.cancel("unknown")).isFalse()
    }

    @Test
    suspend fun `a timed-out verification yields an errored result and is removed from active`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (session, _) = fakeSession(scope)
        val manager = manager(concurrency = 1, timeout = 50.milliseconds)

        val result = manager.run(session, verifyRequest(), VerificationKind.Verify) {
            delay(10.seconds)
            "never"
        }.await()

        assertThat(result).isInstanceOf(VerificationCaseResult::class.java)
        val errored = result as VerificationCaseResult
        assertThat(errored.status()).isEqualTo(VerificationStatus.ERRORED)
        assertThat(errored.message()).contains("timed out")
        assertThat(errored.portfolioId()).isEqualTo("portfolio-1")
        assertThat(manager.activeFor("session-1")).isEmpty()
    }

    @Test
    suspend fun `a failing verification yields an errored result and is removed from active`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (session, _) = fakeSession(scope)
        val manager = manager(concurrency = 1)

        val result = manager.run(session, verifyRequest(), VerificationKind.Verify) {
            throw RuntimeException("verifier exploded")
        }.await()

        assertThat(result).isInstanceOf(VerificationCaseResult::class.java)
        val errored = result as VerificationCaseResult
        assertThat(errored.status()).isEqualTo(VerificationStatus.ERRORED)
        assertThat(errored.message()).contains("verifier exploded")
        assertThat(manager.activeFor("session-1")).isEmpty()
    }

    @Test
    suspend fun `cancelForSession cancels all sessions in-flight work`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (session, _) = fakeSession(scope)
        val manager = manager(concurrency = 2)
        val started1 = CompletableDeferred<Unit>()
        val started2 = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()

        val f1 = manager.run(session, verifyRequest(), VerificationKind.Verify) {
            started1.complete(Unit)
            never.await()
            null
        }
        val f2 = manager.run(session, verifyRequest(), VerificationKind.Verify) {
            started2.complete(Unit)
            never.await()
            null
        }
        started1.await()
        started2.await()

        val cancelled = manager.cancelForSession("session-1")
        awaitSilently(f1)
        awaitSilently(f2)

        assertThat(cancelled).isEqualTo(2)
        assertThat(manager.activeFor("session-1")).isEmpty()
    }

    @Test
    suspend fun `verifications respect the concurrency limit`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (session, _) = fakeSession(scope)
        val manager = manager(concurrency = 1)
        val firstAcquired = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var secondAcquired = false

        val first = manager.run(session, verifyRequest(), VerificationKind.Verify) {
            firstAcquired.complete(Unit)
            releaseFirst.await()
            "first"
        }
        firstAcquired.await()

        val second = manager.run(session, verifyRequest(), VerificationKind.Verify) {
            secondAcquired = true
            "second"
        }
        delay(50.milliseconds)
        assertThat(secondAcquired).isFalse()

        releaseFirst.complete(Unit)
        first.await()
        withTimeout(2.seconds) {
            second.await()
        }
        assertThat(secondAcquired).isTrue()
    }

    private suspend fun <T> awaitSilently(future: CompletableFuture<T>) {
        try {
            future.await()
        } catch (_: CancellationException) {
            // expected when the underlying job was cancelled
        }
    }
}
