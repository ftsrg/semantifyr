/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.utils

inline fun <reified T, reified C : Set<T>> C.except(other: T) = asSequence().filter { it != other }.toSet()

class MultiMap<K, V> {
    private val map = hashMapOf<K, MutableCollection<V>>()

    var size: Int = 0
        private set
    val values: Collection<Collection<V>>
        get() = map.values
    val flatValues: Collection<V>
        get() = map.values.flatten()

    fun isEmpty(): Boolean {
        return map.isEmpty()
    }

    operator fun get(key: K): Collection<V> {
        return map[key] ?: emptyList()
    }

    fun containsValue(value: V): Boolean {
        return flatValues.contains(value)
    }

    fun putAll(key: K, value: Collection<V>) {
        val list = map.getOrPut(key) {
            mutableListOf()
        }
        list.addAll(value)

        size += value.size
    }

    fun put(key: K, value: V) {
        val list = map.getOrPut(key) {
            mutableListOf()
        }

        list.add(value)

        size++
    }

    fun remove(key: K, value: V): Boolean {
        if (map.containsKey(key)) {
            val list = map[key]!!

            val removed = list.remove(value)

            if (list.isEmpty()) {
                map.remove(key)
            }

            if (removed) {
                size--
            }

            return removed
        }

        return false
    }

    fun containsKey(key: K) = map.containsKey(key)

    fun remove(key: K) {
        val removed = map.remove(key)
        size -= removed?.size ?: 0
    }

}
