/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang

import com.google.inject.Inject
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.CascadeComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComparisonOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDirection
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ExecutionTrace
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaRoot
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GuardEvaluation
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NegationOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Persistency
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RecordType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SchedulingOrder
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SetTimeoutAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ShallowHistoryState
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StatechartDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeUnit
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TransitionPriority
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.testing.util.ParseHelper
import org.junit.jupiter.api.Test

@InjectWithGamma
class GammaGrammarTest {

    @Inject
    private lateinit var parseHelper: ParseHelper<GammaRoot>

    private fun parse(text: String): GammaRoot {
        val root = parseHelper.parse(text.trimIndent())
        assertThat(root.eResource().errors).isEmpty()
        return root
    }

    private fun statechart(text: String): StatechartDeclaration {
        return (parse(text) as GammaModelPackage).components.single() as StatechartDeclaration
    }

    @Test
    fun `an unannotated statechart takes Gamma's default of every variation point`() {
        val statechart = statechart(
            """
                package test
                statechart Machine {
                }
            """,
        )

        assertThat(statechart.schedulingOrder).isEqualTo(SchedulingOrder.TOP_DOWN)
        assertThat(statechart.transitionPriority).isEqualTo(TransitionPriority.OFF)
        assertThat(statechart.guardEvaluation).isEqualTo(GuardEvaluation.ON_THE_FLY)
    }

    @Test
    fun `statechart annotations are written in any order`() {
        val statechart = statechart(
            """
                package test
                @GuardEvaluation = beginning-of-step
                @TransitionPriority = value-based
                @RegionSchedule = bottom-up
                statechart Machine {
                }
            """,
        )

        assertThat(statechart.schedulingOrder).isEqualTo(SchedulingOrder.BOTTOM_UP)
        assertThat(statechart.transitionPriority).isEqualTo(TransitionPriority.VALUE_BASED)
        assertThat(statechart.guardEvaluation).isEqualTo(GuardEvaluation.BEGINNING_OF_STEP)
    }

    @Test
    fun `a transition carries its id and priority`() {
        val statechart = statechart(
            """
                package test
                statechart Machine {
                    region Main {
                        initial Start
                        shallow history History
                        state Idle
                    }
                    @(First)
                    transition (2) from Start to Idle
                }
            """,
        )

        val transition = statechart.transitions.single()
        assertThat(transition.name).isEqualTo("First")
        assertThat(transition.priority).isEqualTo(2)
        assertThat(statechart.regions.single().stateNodes[1]).isInstanceOf(ShallowHistoryState::class.java)
    }

    @Test
    fun `not binds tighter than and, and tighter than or, equality looser than ordering`() {
        val statechart = statechart(
            """
                package test
                statechart Machine {
                    var a : integer
                    var b : boolean
                    region Main {
                        initial Start
                        state Idle
                    }
                    transition from Start to Idle [not a = 1 or b and a < 2 = b]
                }
            """,
        )

        val guard = statechart.transitions.single().guard as BooleanOperator
        assertThat(guard.op).isEqualTo(BooleanOp.OR)
        assertThat((guard.left as NegationOperator).body).isInstanceOf(ComparisonOperator::class.java)
        val and = guard.right as BooleanOperator
        assertThat(and.op).isEqualTo(BooleanOp.AND)
        assertThat((and.right as ComparisonOperator).left).isInstanceOf(ComparisonOperator::class.java)
    }

    @Test
    fun `a timeout is set in seconds or milliseconds`() {
        val statechart = statechart(
            """
                package test
                statechart Machine {
                    timeout delay
                    region Main {
                        initial Start
                        state Idle {
                            entry / set delay := 500 ms;
                        }
                    }
                    transition from Start to Idle
                }
            """,
        )

        assertThat(EcoreUtil2.getAllContentsOfType(statechart, SetTimeoutAction::class.java).single().unit).isEqualTo(TimeUnit.MILLISECOND)
    }

    @Test
    fun `interfaces, events, records and cascades take Gamma's syntax`() {
        val gammaPackage = parse(
            """
                package test
                type Point : record {
                    x : integer,
                    y : integer
                }
                interface Base {
                    in persistent event moved(point : Point)
                }
                interface Extended extends Base {
                    inout event ping
                }
                cascade System [
                    port base : provides Base
                ] {
                    component first : System
                    component second : System
                    execute second, first
                }
            """,
        ) as GammaModelPackage

        assertThat(gammaPackage.typeDeclarations.single().type).isInstanceOf(RecordType::class.java)
        val moved = gammaPackage.interfaces.first().events.single()
        assertThat(moved.persistency).isEqualTo(Persistency.PERSISTENT)
        assertThat(moved.parameters.single().name).isEqualTo("point")
        assertThat(gammaPackage.interfaces.last().parents.single().name).isEqualTo("Base")
        assertThat(gammaPackage.interfaces.last().events.single().direction).isEqualTo(EventDirection.INOUT)
        val cascade = gammaPackage.components.single() as CascadeComponentDeclaration
        assertThat(
            cascade.executionList.map {
                it.name
            },
        ).containsExactly("second", "first")
    }

    @Test
    fun `a trace file holds imports, a header naming a case and steps`() {
        val trace = parse(
            """
                import test::model

                trace Checked of Case

                step {
                    act {
                        reset
                    }
                    assert {
                    }
                }
                step {
                    act {
                        raise control.start
                        elapse 1000
                        schedule component
                    }
                    assert {
                        state machine->inner.Main.Idle;
                        variable self.count = 1;
                        transition machine from Idle to Busy when control.start;
                        raise control.done;
                    }
                }
            """,
        ) as ExecutionTrace

        assertThat(trace.steps).hasSize(2)
        assertThat(trace.steps.last().acts).hasSize(3)
        assertThat(trace.steps.last().asserts).hasSize(4)
    }
}
