/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.verification

import hu.bme.mit.semantifyr.backend.SoundnessDirection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UppaalConfigTest {

    private val configs = UppaalConfig.Companion::class.java.declaredMethods.filter {
        it.returnType == UppaalConfig::class.java && it.parameterCount == 0
    }.map {
        it.invoke(UppaalConfig.Companion) as UppaalConfig
    }

    @Test
    fun `a configuration over-approximates when it runs verifyta's over-approximation and under-approximates otherwise`() {
        assertThat(configs).isNotEmpty()
        for (config in configs) {
            val expected = if ("-A" in config.parameters.split(" ")) {
                SoundnessDirection.OverApproximating
            } else {
                SoundnessDirection.UnderApproximating
            }
            assertThat(config.soundness).describedAs(config.id).isEqualTo(expected)
        }
    }
}
