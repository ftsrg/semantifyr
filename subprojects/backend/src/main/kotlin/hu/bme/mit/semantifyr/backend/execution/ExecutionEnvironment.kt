/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

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

    operator fun <T : BackendExecutor> get(key: ExecutorKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        val factory = factories[key] as ExecutorFactory<T>?
        return (factory ?: key.default).invoke()
    }

    fun availability(key: ExecutorKey<*>): AvailabilityReport {
        val factory = factories[key] ?: key.default
        val available = runCatching {
            factory().isAvailable()
        }.getOrElse {
            return AvailabilityReport.Unavailable(
                reason = "${key.name}: probe failed: ${it.message ?: it::class.simpleName}",
                hints = key.unavailableHints,
            )
        }
        return if (available) {
            AvailabilityReport.Available
        } else {
            AvailabilityReport.Unavailable(
                reason = "${key.name}: configured executor is not available on this host",
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
