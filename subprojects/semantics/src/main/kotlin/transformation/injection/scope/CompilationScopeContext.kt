/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.injection.scope

import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.Scope
import com.google.inject.ScopeAnnotation
import com.google.inject.Scopes

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention
@ScopeAnnotation
annotation class CompilationScoped

class CompilationScopeContext {
    private val store = mutableMapOf<Key<*>, Any?>()

    fun <T> containsKey(key: Key<T>): Boolean {
        return store.containsKey(key)
    }

    operator fun <T> get(key: Key<T>): T? {
        return store[key] as T?
    }

    operator fun <T : Any> set(key: Key<T>, instance: T) {
        store[key] = instance
    }
}

class CompilationScope : Scope {
    private var current: CompilationScopeContext? = null

    override fun <T : Any> scope(key: Key<T>, unscoped: Provider<T>): Provider<T> {
        return Provider<T> {
            var instance = current?.get(key)
            if (instance == null && current?.containsKey(key) == false) {
                instance = unscoped.get()

                if (!Scopes.isCircularProxy(instance)) {
                    current?.set(key, instance)
                }
            }
            instance
        }
    }

    fun enter(scope: CompilationScopeContext?) {
        current = scope
    }

    fun exit() {
        current = null
    }

    override fun toString(): String {
        return "Scopes.GuiceCompilationScope"
    }

}
