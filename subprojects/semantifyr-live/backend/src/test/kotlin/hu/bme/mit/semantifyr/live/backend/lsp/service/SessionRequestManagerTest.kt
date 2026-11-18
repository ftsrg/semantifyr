/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionRunContext
import hu.bme.mit.semantifyr.live.backend.lsp.session.withSessionScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.service.OperationCanceledManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds

class SessionRequestManagerTest {

    private val sharedExecutorProvider = SharedExecutorProvider()

    private val requestManager = SessionRequestManager(
        SessionRunContext(sharedExecutorProvider),
        mock<LanguageServices> {
            on { operationCanceledManager } doReturn OperationCanceledManager()
        },
        mock<SessionLanguageServerAccessFactory>(),
    )

    private val events = Collections.synchronizedList(mutableListOf<String>())

    private val firstWriteMayFinish = CountDownLatch(1)

    @AfterEach
    fun closeExecutor() {
        firstWriteMayFinish.countDown()
        sharedExecutorProvider.close()
    }

    private fun blockedWrite(name: String): CompletableFuture<Unit> {
        return requestManager.runWrite(
            mutation = {
                firstWriteMayFinish.await()
                events += "$name mutation"
            },
            validation = { _, _ ->
                events += "$name validation"
            },
        )
    }

    @Test
    suspend fun `a read runs after the pending write`() {
        withTimeout(10.seconds) {
            withSessionScope {
                val write = blockedWrite("write")
                val read = requestManager.runRead {
                    events += "read"
                }
                firstWriteMayFinish.countDown()
                write.await()
                read.await()
            }
        }

        assertThat(events).containsExactly(
            "write mutation",
            "write validation",
            "read",
        )
    }

    @Test
    suspend fun `a newer write skips the pending write's validation and keeps its mutation`() {
        withTimeout(10.seconds) {
            withSessionScope {
                val older = blockedWrite("older")
                val newer = requestManager.runWrite(
                    mutation = {
                        events += "newer mutation"
                    },
                    validation = { _, _ ->
                        events += "newer validation"
                    },
                )
                firstWriteMayFinish.countDown()
                older.await()
                newer.await()
            }
        }

        assertThat(events).containsExactly(
            "older mutation",
            "newer mutation",
            "newer validation",
        )
    }
}
