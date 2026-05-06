/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Provider
import com.google.inject.ScopeAnnotation
import hu.bme.mit.semantifyr.guice.common.ScopeContext
import hu.bme.mit.semantifyr.guice.common.Seed

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ScopeAnnotation
annotation class SessionScoped

private val sessionContext = ScopeContext("SessionScope")

val SessionScope = sessionContext.scope

fun <T> withSessionScope(seed: Seed? = null, block: () -> T): T {
    return sessionContext.withScopeBlocking(seed, block)
}

fun <T : Any> seededKeyProvider(): Provider<T> = Provider {
    error("Value for this binding must be seeded into SessionScope before use")
}
