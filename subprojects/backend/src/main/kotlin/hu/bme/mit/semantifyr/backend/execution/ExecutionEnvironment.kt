/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import java.util.concurrent.ConcurrentHashMap

typealias ExecutorFactory<T> = () -> T

class ExecutorKey<out T : BackendExecutor>(
    val name: String,
    val unavailableHints: List<String> = emptyList(),
    val default: ExecutorFactory<T>,
) {
    override fun toString(): String {
        return "ExecutorKey($name)"
    }
}

class ExecutionEnvironment private constructor(
    private val factories: Map<ExecutorKey<*>, ExecutorFactory<BackendExecutor>>,
) {

    private val executors = ConcurrentHashMap<ExecutorKey<*>, BackendExecutor>()

    private val reports = ConcurrentHashMap<ExecutorKey<*>, Lazy<AvailabilityReport>>()

    operator fun <T : BackendExecutor> get(key: ExecutorKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        return executors.computeIfAbsent(key) {
            (factories[key] ?: key.default).invoke()
        } as T
    }

    fun availability(key: ExecutorKey<*>): AvailabilityReport {
        return reports.computeIfAbsent(key) {
            lazy {
                probe(key)
            }
        }.value
    }

    private fun probe(key: ExecutorKey<*>): AvailabilityReport {
        val available = runCatching {
            get(key).isAvailable()
        }.getOrElse {
            return AvailabilityReport.Unavailable(
                reason = "${key.name} could not be started: ${it.message ?: it::class.simpleName}",
                hints = key.unavailableHints,
            )
        }
        return if (available) {
            AvailabilityReport.Available
        } else {
            AvailabilityReport.Unavailable(
                reason = "${key.name} cannot be run on this machine",
                hints = key.unavailableHints,
            )
        }
    }

    class Builder {
        private val factories = mutableMapOf<ExecutorKey<*>, ExecutorFactory<BackendExecutor>>()

        fun <T : BackendExecutor> put(
            key: ExecutorKey<T>,
            factory: ExecutorFactory<T>,
        ): Builder {
            factories[key] = factory
            return this
        }

        fun build(): ExecutionEnvironment {
            return ExecutionEnvironment(factories.toMap())
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
