/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Key
import com.google.inject.OutOfScopeException
import com.google.inject.Provider
import com.google.inject.Scope
import com.google.inject.ScopeAnnotation
import com.google.inject.Scopes
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Guice scope that holds one instance per LSP session.
 *
 * Backed by a thread-local because session construction is done synchronously inside
 * [SessionManager.runSession] - `enter()`, seeding, `getInstance(LspSession)`, `exit()`
 * all run on the same thread without crossing suspension points. This lets Guice inject
 * session-scoped collaborators like [LspServerRawRunner] and [LspMessageProxy] with proper
 * dependency resolution, breaking construction cycles via the usual `Provider<T>` trick.
 */
class SessionScope : Scope {

    private val scopedValues = ThreadLocal<MutableMap<Key<*>, Any>>()

    /**
     * Execute [block] with a fresh session scope active on the current thread.
     * Use [ScopeSeeder.seed] to install the values other bindings depend on
     * before resolving scoped instances from the injector.
     */
    fun <T> withSessionScope(block: ScopeSeeder.() -> T): T {
        check(scopedValues.get() == null) { "Already inside a SessionScope on this thread" }
        val values = mutableMapOf<Key<*>, Any>()
        scopedValues.set(values)
        try {
            return ScopeSeeder(values).block()
        } finally {
            scopedValues.remove()
        }
    }

    class ScopeSeeder internal constructor(private val values: MutableMap<Key<*>, Any>) {
        fun <T : Any> seed(key: Key<T>, value: T) {
            check(key !in values) { "Key already seeded in SessionScope: $key" }
            values[key] = value
        }

        fun <T : Any> seed(type: Class<T>, value: T) = seed(Key.get(type), value)
    }

    override fun <T : Any?> scope(key: Key<T>, unscoped: Provider<T>): Provider<T> {
        return Provider {
            val values = currentScopeOrThrow()

            @Suppress("UNCHECKED_CAST")
            val existing = values[key] as T?
            if (existing != null) {
                existing
            } else {
                unscoped.get().also { produced ->
                    if (produced != null) {
                        values[key] = produced
                    }
                }
            }
        }
    }

    private fun currentScopeOrThrow(): MutableMap<Key<*>, Any> {
        return scopedValues.get()
            ?: throw OutOfScopeException("Not inside a SessionScope on this thread")
    }

    companion object {
        /**
         * Provider stub for bindings whose value must be supplied via [ScopeSeeder.seed]
         * when the scope is entered. Guice only calls this provider if nothing was seeded,
         * in which case we fail loudly.
         */
        fun <T : Any> seededKeyProvider(): Provider<T> = Provider {
            throw IllegalStateException("Value for this binding must be seeded into SessionScope before use")
        }
    }
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ScopeAnnotation
annotation class SessionScoped

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
