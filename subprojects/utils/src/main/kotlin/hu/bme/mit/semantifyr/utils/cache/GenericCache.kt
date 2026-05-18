/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.cache

import com.github.benmanes.caffeine.cache.Caffeine
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory

const val DEFAULT_MAX_ENTRIES: Long = 64

class GenericCache<K : Any, V : Any>(
    maxEntries: Long = DEFAULT_MAX_ENTRIES,
) {

    private val logger by loggerFactory()

    private val cache = Caffeine.newBuilder()
        .maximumSize(maxEntries)
        .build<K, V>()

    fun getOrCompute(key: K, compute: () -> V): V {
        cache.getIfPresent(key)?.let {
            logger.debug { "Cache hit for key '$key'" }
            return it
        }
        logger.debug { "Cache miss for key '$key', computing" }
        return cache.get(key) {
            compute()
        }
    }

    fun size(): Long {
        return cache.estimatedSize()
    }

    fun clear() {
        cache.invalidateAll()
    }
}
