/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.language

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class LiveLanguageServerModuleTest {

    @Test
    fun `the live verification timeout reaches the verifier`() {
        val config = BackendConfig().withEnv(mapOf("SEMANTIFYR_LIVE_VERIFY_TIMEOUT_SECONDS" to "180"))

        val settings = LiveLanguageServerModule().provideServerSettings(config)

        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(180))
    }
}
