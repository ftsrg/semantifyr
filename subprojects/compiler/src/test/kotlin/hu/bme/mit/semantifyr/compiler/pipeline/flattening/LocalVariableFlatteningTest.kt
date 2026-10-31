/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LocalVariableFlatteningTest : FlatteningTestBase() {

    @Test
    fun `a local var without an initializer is havoced at its declaration`() {
        val flattened = flatten(
            "Host",
            """
                package flattening::locals
                @VerificationCase
                class Host {
                    var x: int := 0
                    redefine tran {
                        var delta: int
                        assume(delta > 0)
                        x := delta
                    }
                    prop { return EF x == 1 }
                }
            """,
        )

        val local = flattened.inlinedOxsts.mainTransition.eAllOfType<LocalVarDeclarationOperation>().single()
        val steps = (local.eContainer() as SequenceOperation).steps
        assertThat(steps[steps.indexOf(local) + 1]).isInstanceOfSatisfying(HavocOperation::class.java) { havoc ->
            assertThat((havoc.reference as ElementReference).element).isSameAs(local)
        }
    }
}
