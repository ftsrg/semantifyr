/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.injection.scope

import com.google.inject.Key
import com.google.inject.OutOfScopeException
import com.google.inject.Provider
import com.google.inject.Scope
import com.google.inject.ScopeAnnotation
import com.google.inject.Scopes

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention
@ScopeAnnotation
annotation class CompilationScoped

object SemantifyrScopes {

    val COMPILATION: Scope = CompilationScope()

    private val threadLocalContext = ThreadLocal<CompilationScopeContext>()

    private class CompilationScopeContext {
        val store = mutableMapOf<Key<*>, Any>()
    }

    private class CompilationScope : Scope {

        companion object {
            private val NULL_INSTANCE = Any()
        }

        override fun <T : Any> scope(key: Key<T>, unscoped: Provider<T>): Provider<T> {
            return Provider<T> {
                val current = threadLocalContext.get()
                if (current == null) {
                    throw OutOfScopeException("Compilation scope running without context!")
                }

                var instance = current.store[key]

                if (instance == null) {
                    instance = unscoped.get()

                    if (!Scopes.isCircularProxy(instance)) {
                        current.store[key] = instance ?: NULL_INSTANCE
                    }
                }

                if (instance == NULL_INSTANCE) {
                    instance = null
                }

                @Suppress("UNCHECKED_CAST")
                instance as T
            }
        }

        override fun toString(): String {
            return "SemantifyrScopes.CompilationScope"
        }

    }

    fun runInScope(block: () -> Unit) {
        val context = CompilationScopeContext()
        val current = threadLocalContext.get()
        if (current != null) {
            throw IllegalStateException("A compilation scope context is already open!")
        }
        try {
            threadLocalContext.set(context)
            block()
        } finally {
            threadLocalContext.set(null)
        }
    }

}
