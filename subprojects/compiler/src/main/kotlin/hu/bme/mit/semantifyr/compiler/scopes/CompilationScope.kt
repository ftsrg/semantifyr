/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.scopes

import com.google.inject.Key
import com.google.inject.Scope
import com.google.inject.ScopeAnnotation
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import hu.bme.mit.semantifyr.scopes.ScopeContext
import hu.bme.mit.semantifyr.scopes.Seed

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ScopeAnnotation
annotation class CompilationScoped

private val compilationContext = ScopeContext("CompilationScope")

val CompilationScope = compilationContext.scope

suspend fun <T> withCompilationScope(request: CompilationRequest, block: suspend () -> T): T {
    return withCompilationScope(
        Seed().apply {
            seed(Key.get(CompilationRequest::class.java), request)
        },
        block,
    )
}

suspend fun <T> withCompilationScope(seed: Seed? = null, block: suspend () -> T): T {
    return compilationContext.withScope(seed, block)
}

fun <T> withCompilationScopeBlocking(request: CompilationRequest, block: () -> T): T {
    return withCompilationScopeBlocking(
        Seed().apply {
            seed(Key.get(CompilationRequest::class.java), request)
        },
        block,
    )
}

fun <T> withCompilationScopeBlocking(seed: Seed? = null, block: () -> T): T {
    return compilationContext.withScopeBlocking(seed, block)
}
