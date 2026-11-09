/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import org.junit.jupiter.api.Test

class LocalInstanceFlatteningTest : FlatteningTestBase() {

    private val workers = """
        package flattening::local_instances
        class Worker {
            var done : bool := false
            tran finish() {
                done := true
            }
        }
        class Other {
            var done : bool := false
            tran finish() {
                done := true
            }
        }
        class Pool {
            contains workers : Worker[0..*]
            contains first : Worker subsets workers
            contains second : Worker subsets workers
            contains spare : Worker
            tran pickOne() {
                var picked : workers := first
                havoc(picked)
                inline picked.finish()
            }
            tran pickTwo() {
                var picks : workers[2] := [first, first]
                havoc(picks)
                inline picks[0].finish()
                inline picks[1].finish()
            }
        }
    """

    @Test
    fun `a havoc of a class-typed local chooses among the instances of its class`() {
        assertFlattenedModel(
            "Host",
            workers + """
                @VerificationCase
                class Host {
                    contains first : Worker
                    contains second : Worker
                    contains other : Other
                    var outside : bool := false
                    redefine tran {
                        var picked : Worker := first
                        havoc(picked)
                        if (picked != first && picked != second) {
                            outside := true
                        }
                    }
                    prop {
                        return AG !outside
                    }
                }
            """,
            $$$$"""
                init {
                }

                tran {
                    var 'picked$$$local0' : int := 0
                    choice {
                        'picked$$$local0' := 0
                    } or {
                        'picked$$$local0' := 1
                    }
                    if ('picked$$$local0' != 0 && 'picked$$$local0' != 1) {
                        '$$root$$outside' := true
                    }
                }

                prop {
                    AG !'$$root$$outside'
                }
            """,
        )
    }

    @Test
    fun `only an optional class-typed local may hold nothing`() {
        assertFlattenedModel(
            "Host",
            workers + """
                @VerificationCase
                class Host {
                    contains first : Worker
                    var empty : bool := false
                    redefine tran {
                        var optional : Worker[0..1] := first
                        havoc(optional)
                        var required : Worker := first
                        havoc(required)
                        if (optional == nothing || required == nothing) {
                            empty := true
                        }
                    }
                    prop {
                        return EF empty
                    }
                }
            """,
            $$$$"""
                init {
                }

                tran {
                    var 'optional$$$local0' : int := 0
                    choice {
                        'optional$$$local0' := -1
                    } or {
                        'optional$$$local0' := 0
                    }
                    var 'required$$$local1' : int := 0
                    choice {
                        'required$$$local1' := 0
                    }
                    if ('optional$$$local0' == -1 || 'required$$$local1' == -1) {
                        '$$root$$empty' := true
                    }
                }

                prop {
                    EF '$$root$$empty'
                }
            """,
        )
    }

    @Test
    fun `a call through an assigned class-typed local dispatches to the instance it holds`() {
        assertFlattenedModel(
            "Host",
            workers + """
                @VerificationCase
                class Host {
                    contains first : Worker
                    contains second : Worker
                    var flip : bool := false
                    redefine tran {
                        var picked : Worker := first
                        if (flip) {
                            picked := second
                        }
                        flip := true
                        inline picked.finish()
                    }
                    prop {
                        return EF second.done
                    }
                }
            """,
            $$$$"""
                init {
                }

                tran {
                    var 'picked$$$local0' : int := 0
                    if ('$$root$$flip') {
                        'picked$$$local0' := 1
                    }
                    '$$root$$flip' := true
                    choice {
                        assume ('picked$$$local0' == 0)
                        '$$root$$first$$done' := true
                    } or {
                        assume ('picked$$$local0' == 1)
                        '$$root$$second$$done' := true
                    }
                }

                prop {
                    EF '$$root$$second$$done'
                }
            """,
        )
    }

    @Test
    fun `a feature-typed local chooses among the instances of its feature on the declaring instance`() {
        assertFlattenedModel(
            "Host",
            workers + """
                @VerificationCase
                class Host {
                    contains pool : Pool
                    redefine tran {
                        inline pool.pickOne()
                    }
                    prop {
                        return AG !pool.spare.done
                    }
                }
            """,
            $$$$"""
                init {
                }

                tran {
                    var 'picked$$$local0' : int := 0
                    choice {
                        'picked$$$local0' := 0
                    } or {
                        'picked$$$local0' := 1
                    }
                    choice {
                        assume ('picked$$$local0' == 0)
                        '$$root$$pool$$first$$done' := true
                    } or {
                        assume ('picked$$$local0' == 1)
                        '$$root$$pool$$second$$done' := true
                    }
                }

                prop {
                    AG !'$$root$$pool$$spare$$done'
                }
            """,
        )
    }

    @Test
    fun `each slot of a class-typed local array holds its own instance`() {
        assertFlattenedModel(
            "Host",
            workers + """
                @VerificationCase
                class Host {
                    contains first : Worker
                    contains second : Worker
                    redefine tran {
                        var queue : Worker[2] := [first, second]
                        inline queue[1].finish()
                    }
                    prop {
                        return EF second.done
                    }
                }
            """,
            $$$$"""
                init {
                }

                tran {
                    var 'queue$$$local0_0' : int := 0
                    var 'queue$$$local0_1' : int := 1
                    choice {
                        assume ('queue$$$local0_1' == 0)
                        '$$root$$first$$done' := true
                    } or {
                        assume ('queue$$$local0_1' == 1)
                        '$$root$$second$$done' := true
                    }
                }

                prop {
                    EF '$$root$$second$$done'
                }
            """,
        )
    }

    @Test
    fun `each slot of a feature-typed local array chooses among the instances of the feature`() {
        assertFlattenedModel(
            "Host",
            workers + """
                @VerificationCase
                class Host {
                    contains pool : Pool
                    redefine tran {
                        inline pool.pickTwo()
                    }
                    prop {
                        return AG !pool.spare.done
                    }
                }
            """,
            $$$$"""
                init {
                }

                tran {
                    var 'picks$$$local0_0' : int := 0
                    var 'picks$$$local0_1' : int := 0
                    choice {
                        'picks$$$local0_0' := 0
                    } or {
                        'picks$$$local0_0' := 1
                    }
                    choice {
                        'picks$$$local0_1' := 0
                    } or {
                        'picks$$$local0_1' := 1
                    }
                    choice {
                        assume ('picks$$$local0_0' == 0)
                        '$$root$$pool$$first$$done' := true
                    } or {
                        assume ('picks$$$local0_0' == 1)
                        '$$root$$pool$$second$$done' := true
                    }
                    choice {
                        assume ('picks$$$local0_1' == 0)
                        '$$root$$pool$$first$$done' := true
                    } or {
                        assume ('picks$$$local0_1' == 1)
                        '$$root$$pool$$second$$done' := true
                    }
                }

                prop {
                    AG !'$$root$$pool$$spare$$done'
                }
            """,
        )
    }
}
