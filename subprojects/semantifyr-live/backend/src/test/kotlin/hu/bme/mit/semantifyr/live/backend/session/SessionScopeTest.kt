/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Key
import com.google.inject.Provider
import hu.bme.mit.semantifyr.guice.common.Seed
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SessionScopeTest {

    @Test
    fun `seeded value is visible to bindings inside the block`() {
        val key = Key.get(String::class.java)
        val seed = Seed().apply { seed(key, "hello") }
        val unscoped = Provider<String> { error("unscoped provider should not run when value is seeded") }

        val value = withSessionScope(seed) {
            SessionScope.scope(key, unscoped).get()
        }

        assertThat(value).isEqualTo("hello")
    }

    @Test
    fun `seededKeyProvider raises when the value was not seeded`() {
        val key = Key.get(String::class.java)

        assertThatThrownBy {
            withSessionScope {
                SessionScope.scope(key, seededKeyProvider<String>()).get()
            }
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
