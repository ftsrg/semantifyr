/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import com.google.inject.Key
import com.google.inject.OutOfScopeException
import com.google.inject.Provider
import com.google.inject.Scope
import com.google.inject.ScopeAnnotation
import com.google.inject.Scopes
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention
@ScopeAnnotation
annotation class CompilationScoped

object SemantifyrScopes {

    private val compilationThreadLocal = ThreadLocal<InstanceStore?>()

    val COMPILATION: Scope = StoreBackedScope("CompilationScope", compilationThreadLocal)

    internal class InstanceStore {
        val store = mutableMapOf<Key<*>, Any>()
    }

    private class StoreBackedScope(
        private val name: String,
        private val threadLocal: ThreadLocal<InstanceStore?>,
    ) : Scope {

        override fun <T : Any> scope(key: Key<T>, unscoped: Provider<T>): Provider<T> {
            return Provider<T> {
                val current = threadLocal.get()
                    ?: throw OutOfScopeException("$name running without context!")

                var instance = current.store[key]

                if (instance == null) {
                    instance = unscoped.get()

                    if (!Scopes.isCircularProxy(instance)) {
                        current.store[key] = instance ?: NULL_INSTANCE
                    }
                }

                if (instance === NULL_INSTANCE) {
                    instance = null
                }

                @Suppress("UNCHECKED_CAST")
                instance as T
            }
        }

        override fun toString(): String {
            return "SemantifyrScopes.$name"
        }
    }

    private class ScopeElement(
        private val store: InstanceStore,
        private val threadLocal: ThreadLocal<InstanceStore?>,
        private val elementKey: CoroutineContext.Key<*>,
    ) : ThreadContextElement<InstanceStore?> {
        override val key: CoroutineContext.Key<*> get() = elementKey

        override fun updateThreadContext(context: CoroutineContext): InstanceStore? {
            val previous = threadLocal.get()
            threadLocal.set(store)
            return previous
        }

        override fun restoreThreadContext(context: CoroutineContext, oldState: InstanceStore?) {
            threadLocal.set(oldState)
        }
    }

    private object CompilationScopeKey : CoroutineContext.Key<ScopeElement>

    class Seed {
        internal val entries = mutableMapOf<Key<*>, Any>()

        fun <T : Any> seed(key: Key<T>, value: T) {
            entries[key] = value
        }
    }

    private fun newStore(seed: Seed?): InstanceStore {
        val store = InstanceStore()
        if (seed != null) store.store.putAll(seed.entries)
        return store
    }

    suspend fun <T> runInCompilationScope(
        seed: Seed? = null,
        block: suspend () -> T,
    ): T {
        check(compilationThreadLocal.get() == null) {
            "A compilation scope is already open on this thread!"
        }
        return withContext(ScopeElement(newStore(seed), compilationThreadLocal, CompilationScopeKey)) {
            block()
        }
    }

    private val NULL_INSTANCE = Any()

}
