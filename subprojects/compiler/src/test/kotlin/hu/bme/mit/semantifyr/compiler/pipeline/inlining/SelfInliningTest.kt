/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import org.junit.jupiter.api.Test

class SelfInliningTest : InliningTestBase() {

    @Test
    fun `self passed as an argument binds the calling instance`() {
        assertInlinedModel(
            "Host",
            testModel(javaClass, "self_argument.semantifyr"),
            """
                init {
                    root.office.registered := root.office.registered + 1
                    root.member.joined := true
                }

                tran {
                }

                prop {
                    AG root.member.joined
                }
            """,
        )
    }
}
