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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReferenceArrayFlatteningTest : FlatteningTestBase() {

    private val jobs = """
        package flattening::reference_arrays
        class Job {
            var done: bool := false
            tran finish() {
                done := true
            }
        }
    """

    private fun rootMember(name: String): String {
        return "${INSTANCE_NAME_SEPARATOR}root$INSTANCE_NAME_SEPARATOR$name"
    }

    private fun assertSlotsHoldInstances(
        flattened: FlattenedCompilationContext,
        variable: String,
        vararg instances: String,
    ) {
        for (index in 0..1) {
            val slot = flattened.variableNamed(rootMember("${variable}_$index"))
            assertThat(slot.typeSpecification.domain.name).isEqualTo("int")
            assertThat(flattened.flatteningInfo.variableInstanceDomains.getValue(slot).map { it.name }).containsExactlyInAnyOrderElementsOf(instances.map { rootMember(it) })
            assertThat(flattened.flatteningInfo.arraySlots.getValue(slot).arrayVariable.name).isEqualTo(variable)
            assertThat(flattened.flatteningInfo.arraySlots.getValue(slot).index).isEqualTo(index)
        }
        assertThat(flattened.inlinedOxsts.eAllOfType<IndexingSuffixExpression>().toList()).isEmpty()
        assertThat(flattened.inlinedOxsts.eAllOfType<HavocOperation>().toList()).isEmpty()
    }

    @Test
    fun `class-typed array slots hold instance identifiers of the class`() {
        val flattened = flatten(
            "Host",
            jobs + """
                @VerificationCase
                class Host {
                    contains first: Job
                    contains second: Job
                    var queue: Job[2] := [first, second]
                    var index: int := 0
                    redefine tran {
                        havoc(queue)
                        queue[index] := second
                        inline queue[index].finish()
                    }
                    prop {
                        return EF queue[0] == second
                    }
                }
            """,
        )

        assertSlotsHoldInstances(flattened, "queue", "first", "second")
    }

    @Test
    fun `feature-typed array slots hold instance identifiers of the feature`() {
        val flattened = flatten(
            "Host",
            jobs + """
                @VerificationCase
                class Host {
                    contains jobs: Job[0..*]
                    contains first: Job subsets jobs
                    contains second: Job subsets jobs
                    contains other: Job
                    var picks: jobs[2] := [first, first]
                    redefine tran {
                        havoc(picks)
                        picks[1] := picks[0]
                    }
                    prop {
                        return AG picks[1] != other
                    }
                }
            """,
        )

        assertSlotsHoldInstances(flattened, "picks", "first", "second")
    }

    @Test
    fun `a class-typed slot that a swap reads after writing is saved as an instance id`() {
        val flattened = flatten(
            "Host",
            jobs + """
                @VerificationCase
                class Host {
                    contains first: Job
                    contains second: Job
                    var queue: Job[2] := [first, second]
                    redefine tran {
                        queue := [queue[1], queue[0]]
                    }
                    prop {
                        return AG queue[0] != queue[1]
                    }
                }
            """,
        )

        val slot0 = Regex.escape("'${rootMember("queue_0")}'")
        val slot1 = Regex.escape("'${rootMember("queue_1")}'")
        val saved = "'${Regex.escape(rootMember("queue_1") + LocalVarNames.LOCAL_VAR_SUFFIX)}\\d+'"
        assertThat(serialize(flattened.inlinedOxsts.mainTransition)).containsPattern("var $saved: int := $slot0\\s+$slot0 := $slot1\\s+$slot1 := $saved")
    }
}
