/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Provider
import com.google.inject.ScopeAnnotation
import hu.bme.mit.semantifyr.guice.common.ScopeContext
import hu.bme.mit.semantifyr.guice.common.Seed
import kotlin.coroutines.CoroutineContext

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ScopeAnnotation
annotation class SessionScoped

private val sessionScopeContext = ScopeContext("SessionScope")

val SessionScope = sessionScopeContext.scope

suspend fun <T> withSessionScope(seed: Seed? = null, block: suspend () -> T): T {
    return sessionScopeContext.withScope(seed, block)
}

suspend fun <T> withSessionScope(lspSession: LspSession, block: suspend () -> T): T {
    val seed = Seed().apply {
        seed(LspSession::class.java, lspSession)
    }
    return sessionScopeContext.withScope(seed, block)
}

fun currentSessionScopeElement(): CoroutineContext.Element {
    return sessionScopeContext.currentCoroutineElement()
}

fun <T : Any> seededKeyProvider(): Provider<T> = Provider {
    error("Value for this binding must be seeded into SessionScope before use")
}
