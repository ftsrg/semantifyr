/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TemporalOperatorInliningTest : InliningTestBase() {

    @Test
    fun `a temporal operator below the root of the property fails compilation`() {
        prepare(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var counter: int := 0
                    prop {
                        return AG (EF counter == 0)
                    }
                }
            """,
        ) {
            assertThatThrownBy {
                inlineAll(it)
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("There are temporal operators that are not the root")
        }
    }
}
