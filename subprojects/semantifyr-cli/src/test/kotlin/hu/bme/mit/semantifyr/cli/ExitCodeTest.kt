/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import hu.bme.mit.semantifyr.cli.common.ExitCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExitCodeTest {

    @Test
    fun `the input error names every extension of OXSTS`() {
        for (extension in oxstsExtensions) {
            assertThat(ExitCode.Input.meaning).contains("`.$extension`")
        }
    }
}
