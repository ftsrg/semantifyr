/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import org.junit.jupiter.api.Test

class SelfInliningTest : InliningTestBase() {

    @Test
    fun `self passed as an argument binds the calling instance`() {
        assertInlinedModel(
            "Host",
            """
                package inlining::tests
                class Group {
                    var registered: int := 0
                    tran register(member: Member) {
                        registered := registered + 1
                        member.joined := true
                    }
                }
                class Member {
                    var joined: bool := false
                    refers group: Group
                    redefine init {
                        inline group.register(self)
                    }
                }
                @VerificationCase
                class Host {
                    contains office: Group
                    contains member: Member {
                        redefine refers group: Group = office
                    }
                    redefine init {
                        inline member.init()
                    }
                    prop {
                        return AG member.joined
                    }
                }
            """,
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
