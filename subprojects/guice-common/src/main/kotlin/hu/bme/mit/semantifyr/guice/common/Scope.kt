/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.guice.common

import com.google.inject.Key
import com.google.inject.OutOfScopeException
import com.google.inject.Provider
import com.google.inject.Scope
import com.google.inject.Scopes
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

private val NULL_INSTANCE = Any()

internal class InstanceStore {
    val store = mutableMapOf<Key<*>, Any>()
}

private class StoreBackedScope(
    private val name: String,
    private val threadLocal: ThreadLocal<InstanceStore?>,
) : Scope {
    override fun <T : Any> scope(
        key: Key<T>,
        unscoped: Provider<T>,
    ) = Provider<T> {
        val current = threadLocal.get() ?: throw OutOfScopeException("$name running without context!")

        synchronized(current.store) {
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
        return "ScopeContext($name)"
    }
}

private class ScopeElement(
    private val store: InstanceStore,
    private val threadLocal: ThreadLocal<InstanceStore?>,
    private val elementKey: CoroutineContext.Key<*>,
) : ThreadContextElement<InstanceStore?> {
    override val key get() = elementKey

    override fun updateThreadContext(context: CoroutineContext): InstanceStore? {
        val previous = threadLocal.get()
        threadLocal.set(store)
        return previous
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: InstanceStore?,
    ) {
        threadLocal.set(oldState)
    }
}

private fun newStore(seed: Seed?): InstanceStore {
    val store = InstanceStore()
    if (seed != null) {
        store.store.putAll(seed.entries)
    }
    return store
}

class Seed {
    internal val entries = mutableMapOf<Key<*>, Any>()

    fun <T : Any> seed(
        key: Key<T>,
        value: T,
    ) {
        entries[key] = value
    }

    fun <T : Any> seed(
        clazz: Class<T>,
        value: T,
    ) {
        entries[Key.get(clazz)] = value
    }
}

class ScopeContext(
    private val name: String,
) {
    private val threadLocal = ThreadLocal<InstanceStore?>()
    private val coroutineKey = NamedCoroutineKey(name)

    val scope: Scope = StoreBackedScope(name, threadLocal)

    suspend fun <T> withScope(
        seed: Seed? = null,
        block: suspend () -> T,
    ): T {
        check(threadLocal.get() == null) {
            "$name is already open on this thread!"
        }
        return withContext(ScopeElement(newStore(seed), threadLocal, coroutineKey)) {
            block()
        }
    }

    fun currentCoroutineElement(): CoroutineContext.Element {
        val store = threadLocal.get() ?: throw OutOfScopeException("$name is not active on this thread")
        return ScopeElement(store, threadLocal, coroutineKey)
    }

    fun <T> withScopeBlocking(
        seed: Seed? = null,
        block: () -> T,
    ): T {
        check(threadLocal.get() == null) {
            "$name is already open on this thread!"
        }
        threadLocal.set(newStore(seed))
        return try {
            block()
        } finally {
            threadLocal.remove()
        }
    }

    private class NamedCoroutineKey(
        private val name: String,
    ) : CoroutineContext.Key<ScopeElement> {
        override fun toString(): String = "ScopeContext.Key($name)"
    }
}
