/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Key
import com.google.inject.OutOfScopeException
import com.google.inject.Provider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SessionScopeTest {

    @Test
    fun `scope returns seeded values inside the block`() {
        val scope = SessionScope()
        val key = Key.get(String::class.java)

        val value = scope.withSessionScope {
            seed(key, "hello")
            scope.scope(key, unscopedProviderThrowing()).get()
        }

        assertThat(value).isEqualTo("hello")
    }

    @Test
    fun `scope caches the value produced by the unscoped provider`() {
        val scope = SessionScope()
        val key = Key.get(String::class.java)
        var callCount = 0
        val provider = Provider {
            callCount++
            "produced"
        }

        scope.withSessionScope {
            val scoped = scope.scope(key, provider)
            scoped.get()
            scoped.get()
            scoped.get()
        }

        assertThat(callCount).isEqualTo(1)
    }

    @Test
    fun `scope throws when accessed outside the block`() {
        val scope = SessionScope()

        assertThatThrownBy { scope.scope(Key.get(String::class.java), unscopedProviderThrowing()).get() }
            .isInstanceOf(OutOfScopeException::class.java)
    }

    @Test
    fun `seededKeyProvider raises when the value was not seeded`() {
        val scope = SessionScope()
        val key = Key.get(String::class.java)

        assertThatThrownBy {
            scope.withSessionScope {
                scope.scope(key, SessionScope.seededKeyProvider<String>()).get()
            }
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `nested scope entry is rejected`() {
        val scope = SessionScope()

        assertThatThrownBy {
            scope.withSessionScope {
                scope.withSessionScope {
                    // noop
                }
            }
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `seeding the same key twice is rejected`() {
        val scope = SessionScope()
        val key = Key.get(String::class.java)

        assertThatThrownBy {
            scope.withSessionScope {
                seed(key, "first")
                seed(key, "second")
            }
        }.isInstanceOf(IllegalStateException::class.java)
    }

    private fun unscopedProviderThrowing(): Provider<String> = Provider { error("unscoped provider should not be called when a value is seeded") }
}
