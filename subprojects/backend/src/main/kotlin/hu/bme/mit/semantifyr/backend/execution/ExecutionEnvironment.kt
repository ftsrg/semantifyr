/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

class ExecutionEnvironment private constructor(
    private val entries: Map<Key<*>, Any>,
) {

    class Key<T : Any>(val name: String)

    operator fun <T : Any> get(key: Key<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return entries[key] as T?
    }

    class Builder {
        private val entries = mutableMapOf<Key<*>, Any>()

        fun <T : Any> put(
            key: Key<T>,
            value: T,
        ): Builder {
            entries[key] = value
            return this
        }

        fun build(): ExecutionEnvironment {
            return ExecutionEnvironment(entries.toMap())
        }
    }

    companion object {
        @JvmStatic
        val Empty = ExecutionEnvironment(emptyMap())

        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }
}
