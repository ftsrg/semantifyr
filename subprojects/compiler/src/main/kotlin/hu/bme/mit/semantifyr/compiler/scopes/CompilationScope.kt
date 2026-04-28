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

val CompilationScope: Scope get() = compilationContext.scope

var Seed.compilationRequest: CompilationRequest
    get() = error("Seed slots are write-only. Read seeded values via injection inside the scope.")
    set(value) {
        seed(Key.get(CompilationRequest::class.java), value)
    }

suspend fun <T> withCompilationScope(request: CompilationRequest, block: suspend () -> T): T {
    return withCompilationScope(
        Seed().apply {
            this.compilationRequest = request
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
            this.compilationRequest = request
        },
        block,
    )
}

fun <T> withCompilationScopeBlocking(seed: Seed? = null, block: () -> T): T {
    return compilationContext.withScopeBlocking(seed, block)
}
