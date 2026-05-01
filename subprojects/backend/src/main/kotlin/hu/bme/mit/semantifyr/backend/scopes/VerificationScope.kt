/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.scopes

import com.google.inject.Scope
import com.google.inject.ScopeAnnotation
import hu.bme.mit.semantifyr.scopes.ScopeContext
import hu.bme.mit.semantifyr.scopes.Seed

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ScopeAnnotation
annotation class VerificationScoped

private val verificationContext = ScopeContext("VerificationScope")

val VerificationScope = verificationContext.scope

suspend fun <T> withVerificationScope(
    seed: Seed? = null,
    block: suspend () -> T,
): T {
    return verificationContext.withScope(seed, block)
}

fun <T> withVerificationScopeBlocking(
    seed: Seed? = null,
    block: () -> T,
): T {
    return verificationContext.withScopeBlocking(seed, block)
}
