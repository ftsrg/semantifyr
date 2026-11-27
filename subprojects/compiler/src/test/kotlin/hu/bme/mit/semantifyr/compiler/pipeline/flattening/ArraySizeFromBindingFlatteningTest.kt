/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.LocalVarNames
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceNames.INSTANCE_NAME_SEPARATOR
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ArraySizeFromBindingFlatteningTest : FlatteningTestBase() {

    private val store = """
        package flattening::size_from_binding
        class Store {
            refers capacity: int
            var slots: int[capacity] := [7, ...]
            tran put(value: int) {
                inline if (capacity > 0) {
                    slots[capacity - 1] := value
                }
            }
        }
    """

    private fun member(vararg path: String): String {
        return path.joinToString("") {
            "$INSTANCE_NAME_SEPARATOR$it"
        }
    }

    private fun FlattenedCompilationContext.variableNames(): List<String> {
        return inlinedOxsts.variables.map {
            it.name
        }
    }

    private fun FlattenedCompilationContext.slotNames(vararg instancePath: String): List<String> {
        val prefix = member("root", *instancePath, "slots") + "_"
        return variableNames().filter {
            it.startsWith(prefix)
        }
    }

    private fun FlattenedCompilationContext.domainNames(slotName: String): List<String> {
        return flatteningInfo.variableInstanceDomains.getValue(variableNamed(slotName)).map {
            it.name
        }
    }

    private fun FlattenedCompilationContext.initialValue(name: String): Int {
        return (variableNamed(name).expression as LiteralInteger).value
    }

    @Test
    fun `each instance holds as many slots as its binding gives, the last written value filling the rest`() {
        val flattened = flatten(
            "Host",
            store + testModel(javaClass, "slots_per_binding.semantifyr"),
        )

        assertThat(flattened.slotNames("one")).containsExactly(member("root", "one", "slots") + "_0")
        assertThat(flattened.flatteningInfo.arraySlots.getValue(flattened.variableNamed(member("root", "one", "slots") + "_0")).size).isEqualTo(1)
        assertThat(flattened.slotNames("three")).hasSize(3)
        for (index in 0..2) {
            val slot = flattened.variableNamed(member("root", "three", "slots") + "_$index")
            assertThat(flattened.initialValue(slot.name)).isEqualTo(7)
            assertThat(flattened.flatteningInfo.arraySlots.getValue(slot).index).isEqualTo(index)
            assertThat(flattened.flatteningInfo.arraySlots.getValue(slot).arrayVariable.name).isEqualTo("slots")
            assertThat(flattened.flatteningInfo.arraySlots.getValue(slot).size).isEqualTo(3)
        }
        assertThat(flattened.inlinedOxsts.eAllOfType<IndexingSuffixExpression>().toList()).isEmpty()
    }

    @Test
    fun `a range with a bound from a binding holds as many slots as its upper bound`() {
        val flattened = flatten(
            "Host",
            testModel(javaClass, "range_bound_from_binding.semantifyr"),
        )

        val items = member("root", "port", "items")
        val values = member("root", "port", "values")
        assertThat(flattened.variableNames()).contains("${items}_0", "${items}_1", "${values}_0", "${values}_1")
        assertThat(flattened.variableNames()).doesNotContain("${items}_2", "${values}_2")
        assertThat(flattened.domainNames("${items}_1")).containsExactly(member("root", "port", "first"))
    }

    @Test
    fun `a size of none has no storage and the declaration with its initial value stands`() {
        val flattened = flatten(
            "Host",
            store + """
                @VerificationCase
                class Host {
                    contains none: Store {
                        redefine refers capacity: int = 0
                    }
                    var count: int := 0
                    redefine tran {
                        inline none.put(1)
                        count := count + 1
                    }
                    redefine prop {
                        return AG count >= 0
                    }
                }
            """,
        )

        assertThat(flattened.slotNames("none")).isEmpty()
        assertThat(flattened.variableNames()).doesNotContain(member("root", "none", "slots"))
    }

    @Test
    fun `an unguarded use at a size of none is an error that names the instance`() {
        assertThatThrownBy {
            flatten(
                "Host",
                store + """
                    @VerificationCase
                    class Host {
                        contains none: Store {
                            redefine refers capacity: int = 0
                        }
                        var first: int := 0
                        redefine tran {
                            first := none.slots[0]
                        }
                        redefine prop {
                            return AG first >= 0
                        }
                    }
                """,
            )
        }.hasMessageContaining("'${member("root", "none", "slots")}' has a size of none and no storage")
    }

    @Test
    fun `a literal of another length than the size of an instance is an error that names the instance`() {
        assertThatThrownBy {
            flatten(
                "Host",
                store + """
                    @VerificationCase
                    class Host {
                        contains pair: Store {
                            redefine refers capacity: int = 2
                        }
                        redefine tran {
                            pair.slots := [1, 2, 3]
                        }
                        redefine prop {
                            return AG pair.slots[0] >= 0
                        }
                    }
                """,
            )
        }.hasMessageContaining("This array has 3 slots where '${member("root", "pair", "slots")}' holds 2.")
    }

    @Test
    fun `a literal of one value is an array of one where the instance has one slot and a length error elsewhere`() {
        val sizes = store + """
            @VerificationCase
            class Host {
                contains store: Store {
                    redefine refers capacity: int = %d
                }
                var same: bool := false
                redefine tran {
                    store.slots := [5]
                    same := store.slots == [5]
                    same := [5] != store.slots
                }
                redefine prop {
                    return AG !same
                }
            }
        """
        val flattened = flatten("Host", sizes.format(1))

        val main = serialize(flattened.inlinedOxsts.mainTransition)
        val slot = quoted(member("root", "store", "slots") + "_0")
        assertThat(main).contains("$slot := 5", "$slot == 5", "5 != $slot")
        assertThatThrownBy {
            flatten("Host", sizes.format(2))
        }.hasMessageContaining("This array has 1 slots where '${member("root", "store", "slots")}' holds 2.")
    }

    @Test
    fun `two arrays of different sizes compared or assigned are an error that names the instance`() {
        val sizes = store + """
            @VerificationCase
            class Host {
                contains pair: Store {
                    redefine refers capacity: int = 2
                }
                contains triple: Store {
                    redefine refers capacity: int = 3
                }
                var same: bool := false
                redefine tran {
                    %s
                }
                redefine prop {
                    return AG !same
                }
            }
        """
        assertThatThrownBy {
            flatten("Host", sizes.format("same := pair.slots == triple.slots"))
        }.hasMessageContaining("This array has 3 slots where '${member("root", "pair", "slots")}' holds 2.")
        assertThatThrownBy {
            flatten("Host", sizes.format("triple.slots := pair.slots"))
        }.hasMessageContaining("This array has 2 slots where '${member("root", "triple", "slots")}' holds 3.")
    }

    @Test
    fun `an initial value longer than the size of an instance is an error that names the instance`() {
        assertThatThrownBy {
            flatten(
                "Host",
                """
                    package flattening::size_from_binding
                    class Store {
                        refers capacity: int
                        var slots: int[capacity] := [1, 2, 3, ...]
                    }
                    @VerificationCase
                    class Host {
                        contains pair: Store {
                            redefine refers capacity: int = 2
                        }
                        redefine prop {
                            return AG pair.slots[0] == 1
                        }
                    }
                """,
            )
        }.hasMessageContaining("Array literal has 3 values but '${member("root", "pair", "slots")}' holds 2 slots.")
    }

    @Test
    fun `a constant index out of the size of an instance is an error that names the instance`() {
        assertThatThrownBy {
            flatten(
                "Host",
                store + """
                    @VerificationCase
                    class Host {
                        contains pair: Store {
                            redefine refers capacity: int = 2
                        }
                        redefine prop {
                            return AG pair.slots[2] == 7
                        }
                    }
                """,
            )
        }.hasMessageContaining("Array index 2 is out of bounds for '${member("root", "pair", "slots")}' (size 2).")
    }

    @Test
    fun `a size under none is an error that names the instance`() {
        assertThatThrownBy {
            flatten(
                "Host",
                store + """
                    @VerificationCase
                    class Host {
                        contains broken: Store {
                            redefine refers capacity: int = -1
                        }
                        redefine prop {
                            return AG true
                        }
                    }
                """,
            )
        }.hasMessageContaining("The size of '${member("root", "broken", "slots")}' is -1..-1")
    }

    @Test
    fun `a class-typed array sized from a binding holds an instance per slot`() {
        val flattened = flatten(
            "Host",
            testModel(javaClass, "class_typed_array.semantifyr"),
        )

        val jobs = member("root", "queue", "jobs")
        for (index in 0..2) {
            val slot = flattened.variableNamed("${jobs}_$index")
            assertThat(slot.typeSpecification.domain.name).isEqualTo("int")
            assertThat(flattened.domainNames(slot.name)).containsExactlyInAnyOrder(member("root", "queue", "first"), member("root", "queue", "second"))
        }
    }

    @Test
    fun `a slot of an array of records is named by its slot and then its field`() {
        val flattened = flatten(
            "Host",
            testModel(javaClass, "array_of_records.semantifyr"),
        )

        val urgent = flattened.variableNamed(member("root", "store", "messages") + "${RecordFlatteningPass.FIELD_SEPARATOR}urgent_2")
        assertThat(flattened.flatteningInfo.arraySlots.getValue(urgent).arrayVariable.name).isEqualTo("messages")
        assertThat(flattened.flatteningInfo.arraySlots.getValue(urgent).index).isEqualTo(2)
        assertThat(flattened.flatteningInfo.recordFields.getValue(urgent).recordVariable.name).isEqualTo("messages")
        assertThat(flattened.flatteningInfo.recordFields.getValue(urgent).fields.single().name).isEqualTo("urgent")
        val kind = flattened.variableNamed(member("root", "store", "messages") + "${RecordFlatteningPass.FIELD_SEPARATOR}kind_1")
        assertThat(flattened.initialValue(kind.name)).isEqualTo(1)
        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).contains("var 'slot${LocalVarNames.LOCAL_VAR_SUFFIX}")
        assertThat(flattened.inlinedOxsts.eAllOfType<IndexingSuffixExpression>().toList()).isEmpty()
    }

    private fun quoted(name: String): String {
        return "'$name'"
    }

    @Test
    fun `a property guarding its read with an if-then-else on the size compiles at a size of none and reads the slot above it`() {
        val sizes = """
            package flattening::size_from_binding
            class Item
            class Store {
                refers capacity: int = 0
                var items: Item[0..capacity] := [nothing, ...]
                prop hasHead(item: Item): bool {
                    return if capacity > 0 then items[0] == item else false
                }
            }
            @VerificationCase
            class Host {
                contains item: Item
                contains store: Store {
                    redefine refers capacity: int = %d
                }
                redefine tran {
                    %s
                }
                redefine prop {
                    return AG !store.hasHead(item)
                }
            }
        """
        val none = flatten("Host", sizes.format(0, ""))
        assertThat(serialize(none.inlinedOxsts.property)).doesNotContain("items")

        val one = flatten("Host", sizes.format(1, "store.items[0] := item"))
        assertThat(serialize(one.inlinedOxsts.property)).contains(quoted(member("root", "store", "items") + "_0"))
    }

    @Test
    fun `a slot of an array whose lower bound is none takes nothing by assignment at an index`() {
        val flattened = flatten(
            "Host",
            testModel(javaClass, "lower_bound_none.semantifyr"),
        )

        val main = serialize(flattened.inlinedOxsts.mainTransition)
        assertThat(main).contains(quoted(member("root", "fixedItems") + "_0") + " := -1")
    }

    @Test
    fun `an if-then-else whose branches differ in size is an error at the branch that names the instance`() {
        val sizes = """
            package flattening::size_from_binding
            class Store {
                refers n: int = 2
                refers m: int = 3
                var a: int[n] := [0, ...]
                var b: int[m] := [0, ...]
                var c: int[n] := [0, ...]
                var flag: bool := false
                var same: bool := false
                tran step() {
                    %s
                }
            }
            @VerificationCase
            class Host {
                contains store: Store
                redefine tran {
                    inline store.step()
                }
                redefine prop {
                    return AG !store.same
                }
            }
        """
        for (operation in listOf(
            "c := if flag then a else b",
            "c := if flag then b else a",
            "same := c == (if flag then a else b)",
            "same := (if flag then b else a) == c",
        )) {
            assertThatThrownBy {
                flatten("Host", sizes.format(operation))
            }.`as`(operation).hasMessageContaining("This array has 3 slots where '${member("root", "store", "c")}' holds 2.")
        }
    }

    @Test
    fun `a lower bound above the upper for an instance is an error that names the instance`() {
        assertThatThrownBy {
            flatten(
                "Host",
                """
                    package flattening::size_from_binding
                    class Store {
                        refers capacity: int
                        var slots: int[2..capacity] := [0, ...]
                    }
                    @VerificationCase
                    class Host {
                        contains store: Store {
                            redefine refers capacity: int = 1
                        }
                        redefine prop {
                            return AG true
                        }
                    }
                """,
            )
        }.hasMessageContaining("The size of '${member("root", "store", "slots")}' is 2..1")
    }

    @Test
    fun `an initial value longer than the size stands at a size of none`() {
        val flattened = flatten(
            "Host",
            testModel(javaClass, "initial_value_longer_than_size.semantifyr"),
        )

        assertThat(flattened.slotNames("store")).isEmpty()
    }
}
