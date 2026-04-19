/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

class ExecutionEnvironment(
    val entries: Map<String, Any>,
) {

    fun with(id: String, spec: Any): ExecutionEnvironment {
        return ExecutionEnvironment(entries + (id to spec))
    }

    class Builder {
        private val entries = mutableMapOf<String, Any>()

        fun put(id: String, spec: Any): Builder {
            entries[id] = spec
            return this
        }

        fun build(): ExecutionEnvironment {
            return ExecutionEnvironment(entries.toMap())
        }
    }

    companion object {
        @JvmField
        val Empty: ExecutionEnvironment = ExecutionEnvironment(emptyMap())

        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }
}
