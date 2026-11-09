/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LocalVariableFlatteningTest : FlatteningTestBase() {

    @Test
    fun `a local variable keeps its initializer and is not havoced`() {
        val flattened = flatten(
            "Host",
            """
                package flattening::locals
                @VerificationCase
                class Host {
                    var counter: int := 0
                    redefine tran {
                        var delta: int := 2
                        assume(counter < 3)
                        counter := counter + delta
                    }
                    prop {
                        return EF counter == 2
                    }
                }
            """,
        )

        val mainTransition = flattened.inlinedOxsts.mainTransition
        val local = mainTransition.eAllOfType<LocalVarDeclarationOperation>().single()
        assertThat(local.expression).isInstanceOfSatisfying(LiteralInteger::class.java) {
            assertThat(it.value).isEqualTo(2)
        }
        assertThat(mainTransition.eAllOfType<HavocOperation>().toList()).isEmpty()
    }
}
