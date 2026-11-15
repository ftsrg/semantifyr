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
            store + """
                @VerificationCase
                class Host {
                    contains one: Store {
                        redefine refers capacity: int = 1
                    }
                    contains three: Store {
                        redefine refers capacity: int = 3
                    }
                    redefine tran {
                        inline one.put(1)
                        inline three.put(2)
                    }
                    prop {
                        return AG one.slots[0] != 5
                    }
                }
            """,
        )

        assertThat(flattened.slotNames("one")).containsExactly(member("root", "one", "slots") + "_0")
        assertThat(flattened.slotNames("three")).hasSize(3)
        for (index in 0..2) {
            val slot = flattened.variableNamed(member("root", "three", "slots") + "_$index")
            assertThat(flattened.initialValue(slot.name)).isEqualTo(7)
            assertThat(flattened.flatteningInfo.arraySlots.getValue(slot).index).isEqualTo(index)
            assertThat(flattened.flatteningInfo.arraySlots.getValue(slot).arrayVariable.name).isEqualTo("slots")
        }
        assertThat(flattened.inlinedOxsts.eAllOfType<IndexingSuffixExpression>().toList()).isEmpty()
    }

    @Test
    fun `a range with a bound from a binding holds as many slots as its upper bound`() {
        val flattened = flatten(
            "Host",
            """
                package flattening::size_from_binding
                class Item
                class Port {
                    refers capacity: int
                    contains first: Item
                    var items: Item[0..capacity] := [nothing, ...]
                    var values: int[1..capacity] := [1, 2, ...]
                    tran accept(index: int) {
                        items[index] := first
                        items := [items[1], nothing]
                    }
                }
                @VerificationCase
                class Host {
                    contains port: Port {
                        redefine refers capacity: int = 2
                    }
                    var index: int := 0
                    redefine tran {
                        inline port.accept(index)
                    }
                    prop {
                        return AG port.values[1] == 2
                    }
                }
            """,
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
                    prop {
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
                        prop {
                            return AG first >= 0
                        }
                    }
                """,
            )
        }.hasMessageContaining("'slots' of instance 'root.none' has a size of none and no storage")
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
                        prop {
                            return AG pair.slots[0] >= 0
                        }
                    }
                """,
            )
        }.hasMessageContaining("This array has 3 slots where 'slots' of instance 'root.pair' holds 2.")
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
                prop {
                    return AG !same
                }
            }
        """
        assertThatThrownBy {
            flatten("Host", sizes.format("same := pair.slots == triple.slots"))
        }.hasMessageContaining("This array has 3 slots where 'slots' of instance 'root.pair' holds 2.")
        assertThatThrownBy {
            flatten("Host", sizes.format("triple.slots := pair.slots"))
        }.hasMessageContaining("This array has 2 slots where 'slots' of instance 'root.triple' holds 3.")
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
                        prop {
                            return AG pair.slots[0] == 1
                        }
                    }
                """,
            )
        }.hasMessageContaining("Array literal has 3 values but 'slots' of instance 'root.pair' holds 2 slots.")
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
                        prop {
                            return AG pair.slots[2] == 7
                        }
                    }
                """,
            )
        }.hasMessageContaining("Array index 2 is out of bounds for 'slots' of instance 'root.pair' (size 2).")
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
                        prop {
                            return AG true
                        }
                    }
                """,
            )
        }.hasMessageContaining("The size of 'slots' of instance 'root.broken' is -1..-1")
    }

    @Test
    fun `a class-typed array sized from a binding holds an instance per slot`() {
        val flattened = flatten(
            "Host",
            """
                package flattening::size_from_binding
                class Job {
                    var done: bool := false
                    tran finish() {
                        done := true
                    }
                }
                class Queue {
                    refers capacity: int
                    contains first: Job
                    contains second: Job
                    var jobs: Job[capacity] := [first, second, ...]
                    tran run(index: int) {
                        inline jobs[index].finish()
                    }
                }
                @VerificationCase
                class Host {
                    contains queue: Queue {
                        redefine refers capacity: int = 3
                    }
                    var index: int := 0
                    redefine tran {
                        havoc(queue.jobs)
                        inline queue.run(index)
                    }
                    prop {
                        return EF queue.jobs[2] == queue.second
                    }
                }
            """,
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
            """
                package flattening::size_from_binding
                record Message {
                    var kind: int := 0
                    var urgent: bool := false
                }
                class Store {
                    refers capacity: int
                    var messages: Message[capacity] := [Message { kind = 1 }, ...]
                    var value: Message := Message { kind = 2 }
                    tran move(index: int) {
                        messages[index].kind := 3
                        messages[index] := value
                        value := messages[0]
                        messages[index] := messages[index + 1]
                        messages[messages[0].kind] := Message { urgent = true }
                    }
                }
                @VerificationCase
                class Host {
                    contains store: Store {
                        redefine refers capacity: int = 3
                    }
                    var index: int := 0
                    redefine tran {
                        inline store.move(index)
                    }
                    prop {
                        return AG !store.messages[2].urgent
                    }
                }
            """,
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
}
