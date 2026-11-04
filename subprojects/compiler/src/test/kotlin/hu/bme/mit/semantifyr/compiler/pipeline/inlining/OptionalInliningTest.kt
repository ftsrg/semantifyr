/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import org.junit.jupiter.api.Test

class OptionalInliningTest : InliningTestBase() {

    private val leaf = """
        package inlining::tests
        class Leaf {
            var count: int := 0
            tran bump() {
                count := count + 1
            }
        }
    """

    @Test
    fun `an optional navigation to a present instance inlines the call`() {
        assertInlinedModel(
            "Host",
            leaf + """
                @VerificationCase
                class Host {
                    contains leaf: Leaf[1]
                    redefine tran {
                        inline leaf?.bump()
                    }
                    prop {
                        return EF leaf.count == 3
                    }
                }
            """,
            """
                init {
                }

                tran {
                    root.leaf.count := root.leaf.count + 1
                }

                prop {
                    EF root.leaf.count == 3
                }
            """,
        )
    }

    @Test
    fun `an optional navigation to an absent instance drops the call and keeps the rest of the step`() {
        assertInlinedModel(
            "Host",
            leaf + """
                @VerificationCase
                class Host {
                    contains leaf: Leaf[0..1]
                    var count: int := 0
                    redefine tran {
                        inline leaf?.bump()
                        assume(count < 1)
                        count := count + 1
                    }
                    prop {
                        return EF count == 1
                    }
                }
            """,
            """
                init {
                }

                tran {
                    assume (root.count < 1)
                    root.count := root.count + 1
                }

                prop {
                    EF root.count == 1
                }
            """,
        )
    }

    @Test
    fun `an inline if on an omitted optional argument takes its nothing branch`() {
        assertInlinedModel(
            "Host",
            """
                package inlining::tests
                class Marker { }
                class Recorder {
                    var value: int := 0
                    var marked: bool := false
                    tran store(value: int, marker: Marker[0..1]) {
                        self.value := value
                        inline if (marker == nothing) {
                            marked := false
                        } else {
                            marked := true
                        }
                    }
                }
                @VerificationCase
                class Host {
                    contains recorder: Recorder[1]
                    contains marker: Marker[1]
                    redefine tran {
                        choice {
                            inline recorder.store(5)
                        } or {
                            inline recorder.store(7, marker)
                        }
                    }
                    prop {
                        return EF (recorder.value == 5 && !recorder.marked)
                    }
                }
            """,
            """
                init {
                }

                tran {
                    choice {
                        root.recorder.value := 5
                        root.recorder.marked := false
                    } or {
                        root.recorder.value := 7
                        root.recorder.marked := true
                    }
                }

                prop {
                    EF root.recorder.value == 5 && !root.recorder.marked
                }
            """,
        )
    }
}
