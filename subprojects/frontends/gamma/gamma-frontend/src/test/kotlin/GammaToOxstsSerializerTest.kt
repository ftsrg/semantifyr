/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaNameMap
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaToOxstsSerializer
import hu.bme.mit.semantifyr.frontends.gamma.serialization.UnmappedGammaConstructException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.testing.util.ParseHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@InjectWithGamma
class GammaToOxstsSerializerTest {

    @Inject
    private lateinit var parseHelper: ParseHelper<GammaModelPackage>

    @Inject
    private lateinit var resourceSetProvider: Provider<XtextResourceSet>

    private fun oxstsLines(source: String): List<String> {
        val gammaPackage = parseHelper.parse(source)
        EcoreUtil.resolveAll(gammaPackage)
        assertThat(gammaPackage.eResource().errors).isEmpty()
        return GammaToOxstsSerializer(GammaNameMap()).transformToOxsts(gammaPackage).lines().map {
            it.trim()
        }
    }

    private fun statechartLines(body: String, annotation: String = ""): List<String> {
        return oxstsLines(
            """
                package test
                interface Control {
                    in event start
                    out event done
                }
                $annotation
                statechart Machine [
                    port control : provides Control
                ] {
                    var count : integer := 0
                    var flag : boolean
                    timeout delay
                    region Main {
                        state Idle
                        state Busy
                        initial MainInitial
                    }
                    transition from MainInitial to Idle
                    $body
                }
            """,
        )
    }

    private fun systemLines(system: String, verificationCases: String = ""): List<String> {
        return oxstsLines(
            """
                package test
                interface Control {
                    in event start
                }
                statechart Leader [
                    port control : requires Control,
                    port workerControl : provides Control
                ] {
                    var count : integer := 0
                    region Main {
                        initial MainInitial
                        state Idle
                    }
                    transition from MainInitial to Idle
                }
                statechart Worker [
                    port control : requires Control
                ] {
                    region Main {
                        initial MainInitial
                        state Idle
                    }
                    transition from MainInitial to Idle
                }
                $system
                $verificationCases
            """,
        )
    }

    private val system = """
        sync System [
            port control : requires Control,
            port spare : provides Control
        ] {
            component leader : Leader
            component worker : Worker
            bind control -> leader.control
            channel [ leader.workerControl ] -o)- [ worker.control ]
        }
    """

    @Test
    fun `a package imports the gamma semantic library`() {
        val lines = oxstsLines("package test")

        assertThat(lines).containsSequence(
            "package _test",
            "",
            "import semantifyr::gamma::expressions",
            "import semantifyr::gamma::variables",
            "import semantifyr::gamma::statecharts",
            "import semantifyr::gamma::components",
            "import semantifyr::gamma::triggers",
            "import semantifyr::gamma::actions",
            "import semantifyr::gamma::events",
            "import semantifyr::gamma::ports",
            "import semantifyr::gamma::verification",
        )
    }

    @Test
    fun `every Gamma name is marked, so none equals a library member or an OXSTS keyword`() {
        val lines = oxstsLines(
            """
                package statecharts::components
                statechart Statechart {
                    region main {
                        initial regions
                        state class
                    }
                    transition from regions to class
                }
            """,
        )

        assertThat(lines).containsSequence(
            "package _statecharts::_components",
        )
        assertThat(lines).containsSequence(
            "class _Statechart : Statechart {",
        )
        assertThat(lines).containsSequence(
            "contains _main: Region subsets regions {",
            "contains _class: State subsets states {",
            "}",
            "contains entry_to_class: EntryTransition subsets entryTransitions {",
            "redefine refers to: State = _class",
        )
    }

    @Test
    fun `each Gamma package is an OXSTS package of its own and each import an OXSTS import`() {
        val resourceSet = resourceSetProvider.get()
        parseHelper.parse(
            """
                package library::control
                interface Control {
                    in event start
                }
            """.trimIndent(),
            URI.createURI("control.gamma"),
            resourceSet,
        )
        val gammaPackage = parseHelper.parse(
            """
                package test::machine
                import library::control
                statechart Machine [
                    port Control : provides Control
                ] {
                }
            """.trimIndent(),
            URI.createURI("machine.gamma"),
            resourceSet,
        )
        EcoreUtil.resolveAll(resourceSet)

        val lines = GammaToOxstsSerializer(GammaNameMap()).transformToOxsts(gammaPackage).lines().map {
            it.trim()
        }

        assertThat(lines.first()).isEqualTo("package _test::_machine")
        assertThat(lines).contains("import _library::_control")
        assertThat(lines).containsSequence(
            "contains _Control: ProvidedPort subsets ports {",
            "redefine contains interface: _library::_control::_Control",
        )
        assertThat(lines).noneMatch {
            it.startsWith("class _Control")
        }
    }

    @Test
    fun `an interface contains its events of their direction with a shared declaration each`() {
        val lines = oxstsLines(
            """
                package test
                interface Control {
                    in event start
                    out event done
                }
            """,
        )

        assertThat(lines).containsSequence(
            "class _Control : Interface {",
            "contains _start: Event subsets inputEvents {",
            "@Shared",
            "redefine contains declaration: EventDeclaration",
            "}",
            "contains _done: Event subsets outputEvents {",
            "@Shared",
            "redefine contains declaration: EventDeclaration",
            "}",
            "}",
        )
    }

    @Test
    fun `a statechart contains its provided and required ports with their interface`() {
        val lines = oxstsLines(
            """
                package test
                interface Control {
                    in event start
                }
                statechart Machine [
                    port command : provides Control,
                    port control : requires Control
                ] {
                }
            """,
        )

        assertThat(lines).containsSequence(
            "contains _command: ProvidedPort subsets ports {",
            "redefine contains interface: _test::_Control",
            "}",
            "contains _control: RequiredPort subsets ports {",
            "redefine contains interface: _test::_Control",
            "}",
            "}",
        )
    }

    @Test
    fun `a statechart without annotations redefines every variant to Gamma's default`() {
        val lines = statechartLines("")

        assertThat(lines).containsSequence(
            "class _Machine : Statechart {",
            "redefine refers regionSchedule: RegionSchedule = RegionSchedule::TopDown",
            "redefine refers transitionPriority: TransitionPriority = TransitionPriority::Off",
            "redefine refers guardEvaluation: GuardEvaluation = GuardEvaluation::OnTheFly",
        )
    }

    @Test
    fun `each statechart annotation redefines the variant the library reads`() {
        val lines = statechartLines(
            "",
            annotation = """
                @RegionSchedule = bottom-up
                @TransitionPriority = order-based
                @GuardEvaluation = beginning-of-step
            """,
        )

        assertThat(lines).containsSequence(
            "class _Machine : Statechart {",
            "redefine refers regionSchedule: RegionSchedule = RegionSchedule::BottomUp",
            "redefine refers transitionPriority: TransitionPriority = TransitionPriority::OrderBased",
            "redefine refers guardEvaluation: GuardEvaluation = GuardEvaluation::BeginningOfStep",
        )
    }

    @Test
    fun `a written priority is emitted under value-based priority only`() {
        val transition = "transition (2) from Idle to Busy when control.start"
        val valueBased = statechartLines(transition, annotation = "@TransitionPriority = value-based")
        val orderBased = statechartLines(transition, annotation = "@TransitionPriority = order-based")

        assertThat(valueBased).contains("redefine refers transitionPriority: TransitionPriority = TransitionPriority::ValueBased")
        assertThat(valueBased).containsSequence(
            "redefine refers to: State = _Busy",
            "redefine refers priority: int = 2",
        )
        assertThat(orderBased).noneMatch {
            it.startsWith("redefine refers priority")
        }
    }

    @Test
    fun `a variable is contained with its type and default value`() {
        val lines = statechartLines("")

        assertThat(lines).containsSequence(
            "contains _count: IntegerVariable subsets variables {",
            "redefine refers defaultValue: int = 0",
            "}",
            "contains _flag: BooleanVariable subsets variables {",
            "}",
        )
    }

    @Test
    fun `a timeout set in seconds counts seconds with the library's delta`() {
        val lines = statechartLines("transition from Idle to Busy / set delay := 2 s;")

        assertThat(lines).contains("contains _delay: Timeout subsets timeouts")
    }

    @Test
    fun `a timeout set in milliseconds counts milliseconds with a delta of one second in milliseconds`() {
        val lines = statechartLines("transition from Idle to Busy / set delay := 500 ms;")

        assertThat(lines).containsSequence(
            "contains _delay: Timeout subsets timeouts {",
            "redefine refers deltaTime: int = 1000",
            "}",
        )
        assertThat(lines).containsSequence(
            "redefine refers timeout: Timeout = _delay",
            "redefine contains expression: LiteralIntegerExpression {",
            "redefine refers value: int = 500",
        )
    }

    @Test
    fun `a region contains its states and its entry transition`() {
        val lines = statechartLines("")

        assertThat(lines).containsSequence(
            "contains _Main: Region subsets regions {",
            "contains _Idle: State subsets states {",
            "}",
            "contains _Busy: State subsets states {",
            "}",
            "contains entry_to_Idle: EntryTransition subsets entryTransitions {",
            "redefine refers to: State = _Idle",
            "}",
            "}",
        )
    }

    @Test
    fun `a state contains its entry and exit actions`() {
        val lines = oxstsLines(
            """
                package test
                interface Control {
                    out event done
                }
                statechart Machine [
                    port control : provides Control
                ] {
                    var count : integer := 0
                    region Main {
                        initial MainInitial
                        state Idle {
                            entry / count := 1;
                            exit / raise control.done;
                        }
                    }
                    transition from MainInitial to Idle
                }
            """,
        )

        assertThat(lines).containsSequence(
            "contains _Idle: State subsets states {",
            "contains entry_assign_count: AssignmentAction subsets entryActions {",
            "redefine refers variable: Variable = _count",
            "redefine contains expression: LiteralIntegerExpression {",
            "redefine refers value: int = 1",
            "}",
            "}",
            "contains exit_raise_control_done: RaiseEventAction subsets exitActions {",
            "redefine refers port: Port = _control",
            "redefine refers event: Event = _control.interface._done",
            "}",
            "}",
        )
    }

    @Test
    fun `a state contains its nested regions`() {
        val lines = oxstsLines(
            """
                package test
                statechart Machine {
                    region Main {
                        state Outer {
                            region Inner {
                                state Nested
                                initial InnerInitial
                            }
                        }
                        initial MainInitial
                    }
                    transition from InnerInitial to Nested
                    transition from MainInitial to Outer
                }
            """,
        )

        assertThat(lines).containsSequence(
            "contains _Outer: State subsets states {",
            "contains _Inner: Region subsets regions {",
            "contains _Nested: State subsets states {",
            "}",
            "contains entry_to_Nested: EntryTransition subsets entryTransitions {",
            "redefine refers to: State = _Nested",
            "}",
            "}",
            "}",
        )
    }

    @Test
    fun `transitions between the same states are told apart by their triggers`() {
        val lines = statechartLines(
            """
                transition from Idle to Busy when control.start
                transition from Idle to Busy when timeout delay
            """,
        )

        assertThat(lines).contains(
            "contains transition_Idle_to_Busy_on_control_start: Transition subsets transitions {",
            "contains transition_Idle_to_Busy_on_delay: Transition subsets transitions {",
        )
    }

    @Test
    fun `transitions that still collide are numbered in model order`() {
        val lines = statechartLines(
            """
                transition from Idle to Busy
                transition from Idle to Busy
            """,
        )

        assertThat(lines).contains(
            "contains transition_Idle_to_Busy: Transition subsets transitions {",
            "contains transition_Idle_to_Busy_2: Transition subsets transitions {",
        )
    }

    @Test
    fun `a transition with an id is named by it`() {
        val lines = statechartLines(
            """
                @(Start)
                transition from Idle to Busy when control.start
            """,
        )

        assertThat(lines).contains("contains _Start: Transition subsets transitions {")
    }

    @Test
    fun `a transition refers to its source and target states`() {
        val lines = statechartLines("transition from Idle to Busy")

        assertThat(lines).containsSequence(
            "contains transition_Idle_to_Busy: Transition subsets transitions {",
            "redefine refers from: State = _Idle",
            "redefine refers to: State = _Busy",
            "}",
        )
    }

    @Test
    fun `a transition across regions lies in its source's region and names its target from the lowest region holding both`() {
        val lines = oxstsLines(
            """
                package test
                interface Control {
                    in event start
                }
                statechart Machine [
                    port control : provides Control
                ] {
                    region Main {
                        initial MainInitial
                        state Outside
                        state Parent {
                            region Left {
                                initial LeftInitial
                                state First
                                state Second
                            }
                        }
                    }
                    transition from MainInitial to Outside
                    transition from LeftInitial to First
                    transition from Outside to Second when control.start
                    transition from First to Outside when control.start
                }
            """,
        )

        assertThat(lines).containsSequence(
            "contains transition_Outside_to_Second: Transition subsets transitions {",
            "redefine refers from: State = _Outside",
            "redefine refers to: State = _Parent._Left._Second",
        )
        assertThat(lines).containsSequence(
            "contains transition_First_to_Outside: Transition subsets transitions {",
            "redefine refers from: State = _First",
            "redefine refers to: State = _Outside",
        )
        assertThat(lines.indexOf("contains transition_First_to_Outside: Transition subsets transitions {")).isLessThan(lines.indexOf("contains transition_Outside_to_Second: Transition subsets transitions {"))
    }

    @Test
    fun `an event trigger refers to the port and the event of its interface`() {
        val lines = statechartLines("transition from Idle to Busy when control.start")

        assertThat(lines).containsSequence(
            "redefine refers to: State = _Busy",
            "redefine contains trigger: EventTrigger {",
            "redefine refers port: Port = _control",
            "redefine refers event: Event = _control.interface._start",
            "}",
        )
    }

    @Test
    fun `a timeout trigger refers to the timeout`() {
        val lines = statechartLines("transition from Busy to Idle when timeout delay")

        assertThat(lines).containsSequence(
            "redefine refers to: State = _Idle",
            "redefine contains trigger: TimeoutTrigger {",
            "redefine refers timeout: Timeout = _delay",
            "}",
        )
    }

    @Test
    fun `a guard contains its expression`() {
        val lines = statechartLines("transition from Idle to Busy [ count < 3 ]")

        assertThat(lines).containsSequence(
            "redefine refers to: State = _Busy",
            "redefine contains guard: Guard {",
            "redefine contains expression: LessThanOperatorExpression {",
            "redefine contains left: VariableExpression {",
            "redefine refers variable: Variable = _count",
            "}",
            "redefine contains right: LiteralIntegerExpression {",
            "redefine refers value: int = 3",
            "}",
            "}",
            "}",
        )
    }

    @Test
    fun `a raise event action refers to the port and the event of its interface`() {
        val lines = statechartLines("transition from Idle to Busy / raise control.done;")

        assertThat(lines).containsSequence(
            "contains raise_control_done: RaiseEventAction subsets actions {",
            "redefine refers port: Port = _control",
            "redefine refers event: Event = _control.interface._done",
            "}",
        )
    }

    @Test
    fun `a set timeout action refers to the timeout and contains its value`() {
        val lines = statechartLines("transition from Idle to Busy / set delay := 2 s;")

        assertThat(lines).containsSequence(
            "contains set_delay: SetTimeoutAction subsets actions {",
            "redefine refers timeout: Timeout = _delay",
            "redefine contains expression: LiteralIntegerExpression {",
            "redefine refers value: int = 2",
            "}",
            "}",
        )
    }

    @Test
    fun `an assignment action refers to the variable and contains its value`() {
        val lines = statechartLines("transition from Idle to Busy / flag := true;")

        assertThat(lines).containsSequence(
            "contains assign_flag: AssignmentAction subsets actions {",
            "redefine refers variable: Variable = _flag",
            "redefine contains expression: LiteralBooleanExpression {",
            "redefine refers value: bool = true",
            "}",
            "}",
        )
    }

    @Test
    fun `arithmetic operators contain their operands`() {
        val lines = statechartLines("transition from Idle to Busy / count := count + 1 - 2 * 3 / 4;")

        assertThat(lines).containsSequence(
            "redefine contains expression: MinusOperatorExpression {",
            "redefine contains left: PlusOperatorExpression {",
            "redefine contains left: VariableExpression {",
            "redefine refers variable: Variable = _count",
            "}",
            "redefine contains right: LiteralIntegerExpression {",
            "redefine refers value: int = 1",
            "}",
            "}",
            "redefine contains right: DivideOperatorExpression {",
            "redefine contains left: MultiplyOperatorExpression {",
            "redefine contains left: LiteralIntegerExpression {",
            "redefine refers value: int = 2",
            "}",
            "redefine contains right: LiteralIntegerExpression {",
            "redefine refers value: int = 3",
            "}",
            "}",
            "redefine contains right: LiteralIntegerExpression {",
            "redefine refers value: int = 4",
            "}",
            "}",
            "}",
        )
    }

    @Test
    fun `unary operators contain their body`() {
        val lines = statechartLines("transition from Idle to Busy / count := - + count;")

        assertThat(lines).containsSequence(
            "redefine contains expression: UnaryMinusExpression {",
            "redefine contains body: UnaryPlusExpression {",
            "redefine contains body: VariableExpression {",
            "redefine refers variable: Variable = _count",
            "}",
            "}",
            "}",
        )
    }

    @Test
    fun `boolean operators and negation contain their operands`() {
        val lines = statechartLines("transition from Idle to Busy / flag := flag and not flag or false;")

        assertThat(lines).containsSequence(
            "redefine contains expression: OrOperatorExpression {",
            "redefine contains left: AndOperatorExpression {",
            "redefine contains left: VariableExpression {",
            "redefine refers variable: Variable = _flag",
            "}",
            "redefine contains right: UnaryNotExpression {",
            "redefine contains body: VariableExpression {",
            "redefine refers variable: Variable = _flag",
            "}",
            "}",
            "}",
            "redefine contains right: LiteralBooleanExpression {",
            "redefine refers value: bool = false",
            "}",
            "}",
        )
    }

    @Test
    fun `comparison operators contain their operands`() {
        val lines = statechartLines(
            """
                transition from Idle to Busy / flag := count < 1; flag := count <= 1; flag := count > 1;
                    flag := count >= 1; flag := count == 1; flag := count != 1;
            """,
        )

        val expressions = lines.filter {
            it.startsWith("redefine contains expression:")
        }

        assertThat(expressions).containsExactly(
            "redefine contains expression: LessThanOperatorExpression {",
            "redefine contains expression: LessThanOrEqualsOperatorExpression {",
            "redefine contains expression: GreaterThanOperatorExpression {",
            "redefine contains expression: GreaterThanOrEqualsOperatorExpression {",
            "redefine contains expression: EqualityOperatorExpression {",
            "redefine contains expression: InequalityOperatorExpression {",
        )
    }

    @Test
    fun `a sync component contains its component instances`() {
        val lines = systemLines(system)

        assertThat(lines).containsSequence(
            "contains _leader: _test::_Leader subsets components",
            "contains _worker: _test::_Worker subsets components",
        )
    }

    @Test
    fun `a sync component contains its unbound ports and refers to the instance port of each bound one`() {
        val lines = systemLines(system)

        assertThat(lines).containsSequence(
            "class _System : SyncComponent {",
            "contains _spare: ProvidedPort subsets ports {",
            "redefine contains interface: _test::_Control",
            "}",
            "refers _control: Port subsets ports = _leader._control",
        )
    }

    @Test
    fun `a channel connects the provided port to the required port`() {
        val lines = systemLines(system)

        assertThat(lines).containsSequence(
            "contains channel_leader_workerControl_to_worker_control: Channel subsets channels {",
            "redefine refers inputPort: Port = _leader._workerControl",
            "redefine refers outputPort: Port = _worker._control",
            "}",
        )
    }

    @Test
    fun `an EF verification case redefines the component and contains an Eventually property`() {
        val lines = systemLines(
            system,
            """
                verification case LeaderIdleReachable of System {
                    EF state leader.Main.Idle
                }
            """,
        )

        assertThat(lines).containsSequence(
            "@VerificationCase",
            "class _LeaderIdleReachable : GammaVerificationCase {",
            "redefine contains component: _test::_System",
            "redefine contains propertyExpression: Eventually {",
            "redefine contains body: IsStateActiveExpression {",
            "redefine refers state: State = component._leader._Main._Idle",
            "}",
            "}",
            "}",
        )
    }

    @Test
    fun `an AG verification case contains a MustAlways property over a variable of an instance`() {
        val lines = systemLines(
            system,
            """
                verification case LeaderCountStaysSmall of System {
                    AG variable leader.count < 2
                }
            """,
        )

        assertThat(lines).containsSequence(
            "redefine contains propertyExpression: MustAlways {",
            "redefine contains body: LessThanOperatorExpression {",
            "redefine contains left: VariableExpression {",
            "redefine refers variable: Variable = component._leader._count",
            "}",
        )
    }

    @Test
    fun `a state atom names the state by its containment path in the instance`() {
        val lines = oxstsLines(
            """
                package test
                statechart Machine {
                    region Main {
                        initial MainInitial
                        state Outer {
                            region Inner {
                                initial InnerInitial
                                state Nested
                            }
                        }
                    }
                    transition from MainInitial to Outer
                    transition from InnerInitial to Nested
                }
                sync System {
                    component machine : Machine
                }
                verification case NestedReachable of System {
                    EF state machine.Inner.Nested
                }
                verification case OwnStateReachable of Machine {
                    EF state Inner.Nested
                }
            """,
        )

        assertThat(lines).contains("redefine refers state: State = component._machine._Main._Outer._Inner._Nested")
        assertThat(lines).contains("redefine refers state: State = component._Main._Outer._Inner._Nested")
    }

    @Test
    fun `the actions of a block are serialized in order`() {
        val lines = statechartLines("transition from Idle to Busy when control.start / { count := 1; raise control.done; }")

        assertThat(lines).containsSubsequence(
            "contains assign_count: AssignmentAction subsets actions {",
            "contains raise_control_done: RaiseEventAction subsets actions {",
        )
    }

    private val historyStatechart = """
        package test
        interface Control {
            in event start
            in event stop
        }
        statechart Machine [
            port control : provides Control
        ] {
            region Main {
                initial MainInitial
                state Working {
                    region Progress {
                        initial ProgressInitial
                        shallow history History
                        state First
                        state Second
                    }
                }
                state Paused
            }
            transition from MainInitial to Working
            transition from ProgressInitial to First
            transition from History to Second
            transition from First to Second when control.start
            transition from Working to Paused when control.stop
            transition from Paused to History when control.stop
        }
    """

    @Test
    fun `a shallow history node is contained in its region with its transition's target beside the entry transition`() {
        val lines = oxstsLines(historyStatechart)

        assertThat(lines).containsSequence(
            "contains _History: ShallowHistory subsets shallowHistory {",
            "redefine refers to: State = _Second",
            "}",
            "contains entry_to_First: EntryTransition subsets entryTransitions {",
            "redefine refers to: State = _First",
            "}",
            "contains transition_First_to_Second: Transition subsets transitions {",
        )
        assertThat(lines).noneMatch {
            it.contains("transition_History_to_Second")
        }
    }

    @Test
    fun `a transition to a history node refers to the node by its path`() {
        val lines = oxstsLines(historyStatechart)

        assertThat(lines).containsSequence(
            "contains transition_Paused_to_History: Transition subsets transitions {",
            "redefine refers from: State = _Paused",
            "redefine refers to: ShallowHistory = _Working._Progress._History",
        )
    }

    private val cascadeSystem = """
        package test
        interface Control {
            in event start
        }
        statechart Worker [
            port control : provides Control
        ] {
            region Main {
                initial MainInitial
                state Idle
            }
            transition from MainInitial to Idle
        }
        cascade Repeated [
            port control : provides Control
        ] {
            component first : Worker
            component second : Worker
            bind control -> first.control
            execute first, second, first
        }
        cascade Declared {
            component first : Worker
            component second : Worker
        }
    """

    @Test
    fun `a cascade composite contains one execution step per entry of its execution list in order`() {
        val lines = oxstsLines(cascadeSystem)

        assertThat(lines).containsSequence(
            "class _Repeated : CascadeComponent {",
            "refers _control: Port subsets ports = _first._control",
            "contains _first: _test::_Worker subsets components",
            "contains _second: _test::_Worker subsets components",
            "contains execute_first: ExecutionStep subsets executionSteps {",
            "redefine refers component: Component = _first",
            "}",
            "contains execute_second: ExecutionStep subsets executionSteps {",
            "redefine refers component: Component = _second",
            "}",
            "contains execute_first_2: ExecutionStep subsets executionSteps {",
            "redefine refers component: Component = _first",
            "}",
        )
    }

    @Test
    fun `a cascade composite without an execution list executes its instances in declaration order`() {
        val lines = oxstsLines(cascadeSystem)

        assertThat(lines).containsSequence(
            "class _Declared : CascadeComponent {",
            "contains _first: _test::_Worker subsets components",
            "contains _second: _test::_Worker subsets components",
            "contains execute_first: ExecutionStep subsets executionSteps {",
            "redefine refers component: Component = _first",
            "}",
            "contains execute_second: ExecutionStep subsets executionSteps {",
            "redefine refers component: Component = _second",
            "}",
        )
    }

    private val parameterStatechart = """
        package test
        type Point : record {
            x : integer,
            y : integer
        }
        interface Measurements {
            in event sample(value : integer, valid : boolean)
            in persistent event level(value : integer)
            in event moved(position : Point)
            out event reading(value : integer)
        }
        statechart Probe [
            port measure : provides Measurements
        ] {
            var kept : integer
            region Main {
                initial MainInitial
                state Waiting
                state Holding
            }
            transition from MainInitial to Waiting
            transition from Waiting to Holding when measure.sample [ measure.sample::valid ] / kept := measure.sample::value; raise measure.reading(kept + 1);
        }
    """

    @Test
    fun `an event contains one shared parameter per declared parameter of its type`() {
        val lines = oxstsLines(parameterStatechart)

        assertThat(lines).containsSequence(
            "contains _sample: Event subsets inputEvents {",
            "@Shared",
            "redefine contains declaration: EventDeclaration",
            "contains _value: IntegerParameter subsets parameters {",
            "@Shared",
            "redefine contains declaration: ParameterDeclaration",
            "}",
            "contains _valid: BooleanParameter subsets parameters {",
            "@Shared",
            "redefine contains declaration: ParameterDeclaration",
            "}",
            "}",
        )
        assertThat(lines).containsSequence(
            "contains _moved: Event subsets inputEvents {",
            "@Shared",
            "redefine contains declaration: EventDeclaration",
            "contains _position: _test::parameter_Point subsets parameters {",
        )
    }

    @Test
    fun `a persistent event redefines its persistency`() {
        val lines = oxstsLines(parameterStatechart)

        assertThat(lines).containsSequence(
            "contains _level: Event subsets inputEvents {",
            "@Shared",
            "redefine contains declaration: EventDeclaration",
            "redefine refers persistent: bool = true",
            "contains _value: IntegerParameter subsets parameters {",
        )
    }

    @Test
    fun `a parameter reference refers to the parameter of the received event`() {
        val lines = oxstsLines(parameterStatechart)

        assertThat(lines).containsSequence(
            "redefine contains guard: Guard {",
            "redefine contains expression: ParameterExpression {",
            "redefine refers parameter: Parameter = _measure.interface._sample._valid",
            "}",
            "}",
        )
        assertThat(lines).containsSequence(
            "contains assign_kept: AssignmentAction subsets actions {",
            "redefine refers variable: Variable = _kept",
            "redefine contains expression: ParameterExpression {",
            "redefine refers parameter: Parameter = _measure.interface._sample._value",
            "}",
            "}",
        )
    }

    @Test
    fun `a raise with arguments contains one argument per parameter of the raised event`() {
        val lines = oxstsLines(parameterStatechart)

        assertThat(lines).containsSequence(
            "contains raise_measure_reading: RaiseEventAction subsets actions {",
            "redefine refers port: Port = _measure",
            "redefine refers event: Event = _measure.interface._reading",
            "contains _value: Argument subsets arguments {",
            "redefine refers parameter: Parameter = _measure.interface._reading._value",
            "redefine contains expression: PlusOperatorExpression {",
        )
    }

    private val recordStatechart = """
        package test
        type Point : record {
            x : integer,
            y : integer
        }
        type Box : record {
            corner : Point,
            open : boolean
        }
        interface Control {
            in event start
        }
        statechart Plotter [
            port control : provides Control
        ] {
            var position : Point := #Point { x := 3 }
            var copy : Point
            var box : Box := #Box { corner := #Point { y := -2 }, open := true }
            region Main {
                initial MainInitial
                state Start
                state Done
            }
            transition from MainInitial to Start
            transition from Start to Done when control.start [ box.corner.x > 4 ] / box.corner.x := 5; copy := position;
        }
    """

    @Test
    fun `a record type is an OXSTS record with field defaults`() {
        val lines = oxstsLines(recordStatechart)

        assertThat(lines).containsSequence(
            "record _Point {",
            "var _x: int := 0",
            "var _y: int := 0",
            "}",
        )
        assertThat(lines).containsSequence(
            "record _Box {",
            "var _corner: _test::_Point := _test::_Point { _x = 0 }",
            "var _open: bool := false",
            "}",
        )
    }

    @Test
    fun `a record type has a parameter class holding the record`() {
        val lines = oxstsLines(recordStatechart)

        assertThat(lines).containsSequence(
            "class parameter_Point : Parameter {",
            "var value: _test::_Point := _test::_Point { _x = 0 }",
            "",
            "redefine prop evaluate(): _test::_Point {",
            "return value",
            "}",
            "",
            "redefine tran set(value: _test::_Point) {",
            "self.value := value",
            "}",
            "",
            "redefine tran reset() {",
            "value := _test::_Point { _x = 0 }",
            "}",
            "",
            "redefine tran havoc() {",
            "havoc(value)",
            "}",
            "}",
        )
    }

    @Test
    fun `a record type has a variable class whose initializer and restore read one default binding per leaf field`() {
        val lines = oxstsLines(recordStatechart)

        assertThat(lines).containsSequence(
            "class variable_Box : Variable {",
            "refers default_corner_x: int = 0",
            "refers default_corner_y: int = 0",
            "refers default_open: bool = false",
            "",
            "var variable: _test::_Box := _test::_Box { _corner = _test::_Point { _x = default_corner_x, _y = default_corner_y }, _open = default_open }",
            "",
            "redefine prop evaluate(): _test::_Box {",
            "return variable",
            "}",
            "",
            "redefine tran set(value: _test::_Box) {",
            "variable := value",
            "}",
            "",
            "redefine tran restore() {",
            "variable := _test::_Box { _corner = _test::_Point { _x = default_corner_x, _y = default_corner_y }, _open = default_open }",
            "}",
            "}",
        )
    }

    @Test
    fun `a record type has one access class per field that reads and updates the field`() {
        val lines = oxstsLines(recordStatechart)

        assertThat(lines).containsSequence(
            "class access_Point_x : FieldAccessExpression {",
            "redefine prop evaluate(): any {",
            "return (operand.evaluate() as _test::_Point)._x",
            "}",
            "",
            "redefine prop updated(original: any, value: any): any {",
            "return _test::_Point { _x = value as int, _y = (original as _test::_Point)._y }",
            "}",
            "}",
        )
        assertThat(lines).containsSequence(
            "class access_Box_corner : FieldAccessExpression {",
            "redefine prop evaluate(): any {",
            "return (operand.evaluate() as _test::_Box)._corner",
            "}",
            "",
            "redefine prop updated(original: any, value: any): any {",
            "return _test::_Box { _corner = value as _test::_Point, _open = (original as _test::_Box)._open }",
            "}",
            "}",
        )
    }

    @Test
    fun `a record variable redefines the default bindings of its initial value`() {
        val lines = oxstsLines(recordStatechart)

        assertThat(lines).containsSequence(
            "contains _position: _test::variable_Point subsets variables {",
            "redefine refers default_x: int = 3",
            "}",
            "contains _copy: _test::variable_Point subsets variables {",
            "}",
            "contains _box: _test::variable_Box subsets variables {",
            "redefine refers default_corner_y: int = -2",
            "redefine refers default_open: bool = true",
            "}",
        )
    }

    @Test
    fun `a nested field read chains the access classes through their operands`() {
        val lines = oxstsLines(recordStatechart)

        assertThat(lines).containsSequence(
            "redefine contains expression: GreaterThanOperatorExpression {",
            "redefine contains left: _test::access_Point_x {",
            "redefine contains operand: _test::access_Box_corner {",
            "redefine contains operand: VariableExpression {",
            "redefine refers variable: Variable = _box",
            "}",
            "}",
            "}",
        )
    }

    @Test
    fun `a nested field write assigns the variable the record updated outermost field first`() {
        val lines = oxstsLines(recordStatechart)

        assertThat(lines).containsSequence(
            "contains assign_box: AssignmentAction subsets actions {",
            "redefine refers variable: Variable = _box",
            "redefine contains expression: FieldUpdateExpression {",
            "redefine contains field: _test::access_Box_corner {",
            "redefine contains operand: VariableExpression {",
            "redefine refers variable: Variable = _box",
            "}",
            "}",
            "redefine contains value: FieldUpdateExpression {",
            "redefine contains field: _test::access_Point_x {",
            "redefine contains operand: _test::access_Box_corner {",
            "redefine contains operand: VariableExpression {",
            "redefine refers variable: Variable = _box",
            "}",
            "}",
            "}",
            "redefine contains value: LiteralIntegerExpression {",
            "redefine refers value: int = 5",
            "}",
            "}",
            "}",
            "}",
        )
    }

    @Test
    fun `a whole record assignment assigns the variable the other record`() {
        val lines = oxstsLines(recordStatechart)

        assertThat(lines).containsSequence(
            "contains assign_copy: AssignmentAction subsets actions {",
            "redefine refers variable: Variable = _copy",
            "redefine contains expression: VariableExpression {",
            "redefine refers variable: Variable = _position",
            "}",
            "}",
        )
    }

    @Test
    fun `an interface with one parent extends the parent's class`() {
        val lines = oxstsLines(
            """
                package test
                interface Greeting {
                    in event hello
                }
                interface Reply extends Greeting {
                    out event welcome
                }
            """,
        )

        assertThat(lines).containsSequence(
            "class _Reply : _test::_Greeting {",
            "contains _welcome: Event subsets outputEvents {",
            "@Shared",
            "redefine contains declaration: EventDeclaration",
            "}",
            "}",
        )
    }

    @Test
    fun `an interface with several parents contains every ancestor's events before its own`() {
        val lines = oxstsLines(
            """
                package test
                interface Greeting {
                    in event hello
                }
                interface Farewell {
                    in event bye
                }
                interface Conversation extends Greeting, Farewell {
                    out event welcome
                }
            """,
        )

        assertThat(lines).containsSubsequence(
            "class _Conversation : Interface {",
            "contains _hello: Event subsets inputEvents {",
            "contains _bye: Event subsets inputEvents {",
            "contains _welcome: Event subsets outputEvents {",
        )
    }

    private val inoutStatechart = """
        package test
        interface Duplex {
            inout event ping
        }
        statechart Caller [
            port line : provides Duplex
        ] {
            region Main {
                initial MainInitial
                state Idle
                state Calling
            }
            transition from MainInitial to Idle
            transition from Idle to Calling when line.ping / raise line.ping;
        }
        statechart Callee [
            port line : requires Duplex
        ] {
            region Main {
                initial MainInitial
                state Idle
                state Calling
            }
            transition from MainInitial to Idle
            transition from Idle to Calling when line.ping / raise line.ping;
        }
    """

    @Test
    fun `an inout event is an input and an output event with a shared declaration each`() {
        val lines = oxstsLines(inoutStatechart)

        assertThat(lines).containsSequence(
            "class _Duplex : Interface {",
            "contains input_ping: Event subsets inputEvents {",
            "@Shared",
            "redefine contains declaration: EventDeclaration",
            "}",
            "contains output_ping: Event subsets outputEvents {",
            "@Shared",
            "redefine contains declaration: EventDeclaration",
            "}",
            "}",
        )
    }

    @Test
    fun `a provided port receives the input half of an inout event and raises its output half`() {
        val lines = oxstsLines(inoutStatechart)
        val caller = lines.subList(lines.indexOf("class _Caller : Statechart {"), lines.indexOf("class _Callee : Statechart {"))

        assertThat(caller).containsSequence(
            "contains raise_line_ping: RaiseEventAction subsets actions {",
            "redefine refers port: Port = _line",
            "redefine refers event: Event = _line.interface.output_ping",
        )
        assertThat(caller).containsSequence(
            "redefine contains trigger: EventTrigger {",
            "redefine refers port: Port = _line",
            "redefine refers event: Event = _line.interface.input_ping",
        )
    }

    @Test
    fun `a required port receives the output half of an inout event and raises its input half`() {
        val lines = oxstsLines(inoutStatechart)
        val callee = lines.subList(lines.indexOf("class _Callee : Statechart {"), lines.size)

        assertThat(callee).containsSequence(
            "contains raise_line_ping: RaiseEventAction subsets actions {",
            "redefine refers port: Port = _line",
            "redefine refers event: Event = _line.interface.input_ping",
        )
        assertThat(callee).containsSequence(
            "redefine contains trigger: EventTrigger {",
            "redefine refers port: Port = _line",
            "redefine refers event: Event = _line.interface.output_ping",
        )
    }

    @Test
    fun `a raise atom on a bound port of a composite names the event on the instance port the binding reaches`() {
        val lines = oxstsLines(
            """
                package test
                interface Control {
                    out event done
                }
                statechart Machine [
                    port control : provides Control
                ] {
                }
                sync System [
                    port output : provides Control
                ] {
                    component machine : Machine
                    bind output -> machine.control
                }
                verification case Raised of System {
                    EF raise output.done
                }
            """,
        )

        assertThat(lines).containsSequence(
            "redefine contains body: IsEventRaisedExpression {",
            "redefine refers port: Port = component._machine._control",
            "redefine refers event: Event = component._machine._control.interface._done",
            "}",
        )
    }

    @Test
    fun `a port shadowed by a state of the same name is reached through the statechart`() {
        val lines = oxstsLines(
            """
                package test
                interface Alarm {
                    out event error
                }
                statechart Monitor [
                    port Error : provides Alarm
                ] {
                    timeout Busy
                    region Main {
                        initial MainInitial
                        state Busy
                        state Error
                    }
                    transition from MainInitial to Busy
                    transition from Busy to Error when timeout Busy / raise Error.error;
                }
            """,
        )

        assertThat(lines).containsSequence(
            "redefine contains trigger: TimeoutTrigger {",
            "redefine refers timeout: Timeout = (statechart as _test::_Monitor)._Busy",
            "}",
            "contains raise_Error_error: RaiseEventAction subsets actions {",
            "redefine refers port: Port = (statechart as _test::_Monitor)._Error",
            "redefine refers event: Event = (statechart as _test::_Monitor)._Error.interface._error",
            "}",
        )
    }

    @Test
    fun `a raise atom reads whether the event is raised on the port of the instance`() {
        val lines = oxstsLines(
            """
                package test
                interface Control {
                    out event done
                }
                statechart Machine [
                    port control : provides Control
                ] {
                }
                sync System {
                    component machine : Machine
                }
                verification case Raised of System {
                    EF raise machine.control.done
                }
            """,
        )

        assertThat(lines).containsSequence(
            "redefine contains propertyExpression: Eventually {",
            "redefine contains body: IsEventRaisedExpression {",
            "redefine refers port: Port = component._machine._control",
            "redefine refers event: Event = component._machine._control.interface._done",
            "}",
            "}",
        )
    }

    private fun asynchronousLines(): List<String> {
        return oxstsLines(
            """
                package test
                interface Signals {
                    in event first(value : integer)
                    in event second
                }
                interface Replies {
                    out event done
                }
                @Asynchronous
                statechart Sender [
                    port signals : provides Signals,
                    port replies : provides Replies
                ] {
                }
                statechart Receiver [
                    port signals : provides Signals,
                    port replies : requires Replies
                ] {
                }
                adapter Buffered of component receiver : Receiver {
                    clock ticker (rate = 3 ms)
                    when ticker / run
                    queue low (priority = 1, capacity = 2) {
                        replies.any, ticker
                    }
                    queue high (priority = 2, capacity = 1) {
                        signals.any
                    }
                }
                scheduled-async System [
                    port signals : provides Signals
                ] {
                    component sender : Sender
                    component buffered : Buffered
                    bind signals -> sender.signals
                    channel [ sender.replies ] -o)- [ buffered.replies ]
                    execute sender, buffered, buffered
                }
            """,
        )
    }

    @Test
    fun `a queue is a generated class whose storage has the capacity as a literal`() {
        val lines = asynchronousLines()

        assertThat(lines).containsSequence(
            "class queue_Buffered_high : MessageQueue {",
            "redefine refers capacity: int = 1",
            "redefine refers priority: int = 2",
        )
        assertThat(lines).contains(
            "var identifiers: int[2] := [0, 0]",
            "var values_1_value: int[2] := [0, 0]",
            "var identifiers: int[2] := [0, 0]",
        )
        assertThat(lines).containsSequence(
            "tran enqueue_1(value_value: int) {",
            "identifiers[size] := 1",
            "values_1_value[size] := value_value",
            "size := size + 1",
        )
    }

    @Test
    fun `queues are ranked by descending priority and a clock tick runs the component`() {
        val lines = asynchronousLines()

        assertThat(lines).containsSequence(
            "contains _low: _test::queue_Buffered_low subsets queues {",
            "redefine refers position: int = 0",
            "redefine refers rank: int = 1",
        )
        assertThat(lines).containsSequence(
            "contains _high: _test::queue_Buffered_high subsets queues {",
            "redefine refers position: int = 1",
            "redefine refers rank: int = 0",
        )
        assertThat(lines).containsSequence(
            "inline shift()",
            "if (identifier == 2) {",
            "inline adapter.run()",
        )
        assertThat(lines).containsSequence(
            "inline if (clock == clock_2) {",
            "inline dropOldestWhenFull()",
            "if (!isFull()) {",
            "inline enqueue_2()",
        )
    }

    @Test
    fun `a reset message resets the wrapped component in place of a run and a queue reset replaces both`() {
        val lines = oxstsLines(
            """
                package test
                interface Signals {
                    in event first
                    in event second
                    in event third
                }
                statechart Receiver [
                    port signals : provides Signals
                ] {
                }
                adapter Buffered of component receiver : Receiver {
                    when signals.first / run
                    when signals.second / reset
                    when signals.third / reset
                    when signals.third / reset-queue
                    queue messages (priority = 1, capacity = 2) {
                        signals.any
                    }
                }
            """,
        )

        assertThat(lines).containsSequence(
            "if (identifier == 1) {",
            "inline adapter.run()",
            "}",
            "if (identifier == 2) {",
            "inline adapter.resetComponent()",
            "}",
            "if (identifier == 3) {",
            "inline clear()",
            "}",
        )
    }

    @Test
    fun `a queue discarding the oldest message binds it and redefines the shift that drops it`() {
        val lines = oxstsLines(
            """
                package test
                interface Signals {
                    in event first(value : integer)
                }
                statechart Receiver [
                    port signals : provides Signals
                ] {
                }
                adapter Buffered of component receiver : Receiver {
                    when any / run
                    queue messages (priority = 1, capacity = 2, discard = oldest) {
                        signals.any
                    }
                }
            """,
        )

        assertThat(lines).containsSequence(
            "class queue_Buffered_messages : MessageQueue {",
            "redefine refers capacity: int = 2",
            "redefine refers priority: int = 1",
            "redefine refers discardsOldest: bool = true",
        )
        assertThat(lines).containsSequence(
            "redefine tran shift() {",
            "inline for (index in 0..<capacity - 1) {",
            "identifiers[index] := identifiers[index + 1]",
            "values_1_value[index] := values_1_value[index + 1]",
        )
    }

    @Test
    fun `a queue discarding the incoming message leaves the library default`() {
        val lines = asynchronousLines()

        assertThat(lines).noneMatch {
            it.contains("discardsOldest")
        }
    }

    @Test
    fun `an output reaching several queues is dispatched to each and reset by the last dispatch only`() {
        val lines = oxstsLines(
            """
                package test
                interface Signals {
                    out event first(value : integer)
                }
                @Asynchronous
                statechart Sender [
                    port signals : provides Signals
                ] {
                }
                @Asynchronous
                statechart Receiver [
                    port signals : requires Signals
                ] {
                }
                async System {
                    component sender : Sender
                    component left : Receiver
                    component right : Receiver
                    channel [ sender.signals ] -o)- [ left.signals ]
                    channel [ sender.signals ] -o)- [ right.signals ]
                }
            """,
        )

        assertThat(lines).containsSequence(
            "contains dispatch_System_sender_signals_first_to_left_signals: _test::dispatch_System_sender_signals_first_to_left_signals subsets dispatches {",
            "redefine refers source: AsynchronousAdapter = _sender",
            "redefine refers event: Event = _sender.wrapped._signals.interface._first",
            "redefine refers target: _test::queue_Receiver_ReceiverMessageQueue = _left.queue_ReceiverMessageQueue",
            "redefine refers parameter_value: Parameter = _sender.wrapped._signals.interface._first._value",
            "redefine refers resetsSource: bool = false",
            "}",
            "contains dispatch_System_sender_signals_first_to_right_signals: _test::dispatch_System_sender_signals_first_to_right_signals subsets dispatches {",
            "redefine refers source: AsynchronousAdapter = _sender",
            "redefine refers event: Event = _sender.wrapped._signals.interface._first",
            "redefine refers target: _test::queue_Receiver_ReceiverMessageQueue = _right.queue_ReceiverMessageQueue",
            "redefine refers parameter_value: Parameter = _sender.wrapped._signals.interface._first._value",
            "}",
        )
    }

    @Test
    fun `an asynchronous statechart is unfolded into Gamma's default adapter`() {
        val lines = asynchronousLines()

        assertThat(lines).containsSequence(
            "class adapter_Sender : AsynchronousAdapter {",
            "redefine contains wrapped: _test::_Sender",
            "refers _signals: Port subsets ports = wrapped._signals",
            "refers _replies: Port subsets ports = wrapped._replies",
            "refers environmental_SenderMessageQueue: bool = true",
            "contains queue_SenderMessageQueue: _test::queue_Sender_SenderMessageQueue subsets queues {",
        )
        assertThat(lines).contains("redefine refers capacity: int = 4")
    }

    @Test
    fun `a composite binds per instance which queues the environment fills, how often it runs and where its outputs go`() {
        val lines = asynchronousLines()

        assertThat(lines).containsSequence(
            "contains _sender: _test::adapter_Sender subsets adapters {",
            "redefine refers environmental_SenderMessageQueue: bool = true",
            "}",
            "contains _buffered: _test::_Buffered subsets adapters {",
            "redefine refers scheduleCount: int = 2",
            "redefine refers environmental_low: bool = false",
            "redefine refers environmental_high: bool = false",
        )
        assertThat(lines).containsSequence(
            "contains dispatch_System_sender_replies_done_to_buffered_replies: _test::dispatch_System_sender_replies_done_to_buffered_replies subsets dispatches {",
            "redefine refers source: AsynchronousAdapter = _sender",
            "redefine refers event: Event = _sender.wrapped._replies.interface._done",
            "redefine refers target: _test::queue_Buffered_low = _buffered._low",
        )
        assertThat(lines).containsSequence(
            "contains execute_sender: AsynchronousExecutionStep subsets executionSteps {",
            "redefine refers adapter: AsynchronousAdapter = _sender",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unmappedConstructs")
    fun `a construct the serializer does not map yet is a located diagnostic`(construct: String, source: String) {
        val resourceSet = resourceSetProvider.get()
        parseHelper.parse("package other", URI.createURI("other.gamma"), resourceSet)
        val gammaPackage = parseHelper.parse(source.trimIndent(), URI.createURI("test.gamma"), resourceSet)
        EcoreUtil.resolveAll(gammaPackage)
        assertThat(gammaPackage.eResource().errors).isEmpty()

        assertThatThrownBy {
            GammaToOxstsSerializer(GammaNameMap()).transformToOxsts(gammaPackage)
        }.isInstanceOf(UnmappedGammaConstructException::class.java).hasMessageContaining(construct).hasMessageContaining("is not mapped to OXSTS yet").hasMessageMatching("(?s).*:\\d+: .*")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedConstructs")
    fun `a construct the user decided to refuse is a located diagnostic`(construct: String, source: String) {
        val gammaPackage = parseHelper.parse(source.trimIndent(), URI.createURI("test.gamma"), resourceSetProvider.get())
        EcoreUtil.resolveAll(gammaPackage)
        assertThat(gammaPackage.eResource().errors).isEmpty()

        assertThatThrownBy {
            GammaToOxstsSerializer(GammaNameMap()).transformToOxsts(gammaPackage)
        }.isInstanceOf(UnmappedGammaConstructException::class.java).hasMessageContaining("$construct is refused").hasMessageMatching("(?s)test.gamma:\\d+: .*")
    }

    companion object {
        @JvmStatic
        fun unmappedConstructs(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "a transition to the history node 'History' of a top-level region",
                    """
                        package test
                        interface Control {
                            in event start
                        }
                        statechart Machine [
                            port control : provides Control
                        ] {
                            region Main {
                                initial MainInitial
                                shallow history History
                                state Idle
                            }
                            transition from MainInitial to Idle
                            transition from Idle to History when control.start
                        }
                    """,
                ),
                Arguments.of(
                    "an initial transition with a trigger or guard",
                    """
                        package test
                        statechart Machine {
                            var count : integer
                            region Main {
                                initial MainInitial
                                state Idle
                            }
                            transition from MainInitial to Idle [count = 0]
                        }
                    """,
                ),
                Arguments.of(
                    "an initial transition to 'Nested' in another region",
                    """
                        package test
                        statechart Machine {
                            region Main {
                                initial MainInitial
                                state Outer {
                                    region Inner {
                                        state Nested
                                    }
                                }
                            }
                            transition from MainInitial to Nested
                        }
                    """,
                ),
                Arguments.of(
                    "a transition from 'Left' to 'Right' between orthogonal regions",
                    """
                        package test
                        interface Control {
                            in event start
                        }
                        statechart Machine [
                            port control : provides Control
                        ] {
                            region Main {
                                initial MainInitial
                                state Outer {
                                    region First {
                                        initial FirstInitial
                                        state Left
                                    }
                                    region Second {
                                        initial SecondInitial
                                        state Right
                                    }
                                }
                            }
                            transition from MainInitial to Outer
                            transition from FirstInitial to Left
                            transition from SecondInitial to Right
                            transition from Left to Right when control.start
                        }
                    """,
                ),
                Arguments.of(
                    "an anonymous record type",
                    """
                        package test
                        type Box : record {
                            corner : record {
                                x : integer
                            }
                        }
                    """,
                ),
                Arguments.of(
                    "a record literal outside a variable's initial value",
                    """
                        package test
                        type Point : record {
                            x : integer
                        }
                        statechart Machine {
                            var position : Point
                            timeout delay
                            region Main {
                                initial MainInitial
                                state Idle
                            }
                            transition from MainInitial to Idle
                            transition from Idle to Idle when timeout delay / position := #Point { x := 1 };
                        }
                    """,
                ),
                Arguments.of(
                    "the asynchronous composite 'Inner' inside the asynchronous composite 'Outer'",
                    """
                        package test
                        interface Signals {
                            in event first
                        }
                        statechart Receiver [
                            port signals : provides Signals
                        ] {
                        }
                        adapter Buffered of component receiver : Receiver {
                            when any / run
                            queue messages (priority = 1, capacity = 1) {
                                signals.any
                            }
                        }
                        async Inner {
                            component buffered : Buffered
                        }
                        async Outer {
                            component inner : Inner
                        }
                    """,
                ),
                Arguments.of(
                    "the queue 'messages' of 'Ticking' storing messages of the environment and clock ticks",
                    """
                        package test
                        interface Signals {
                            in event first
                        }
                        statechart Receiver [
                            port signals : provides Signals
                        ] {
                        }
                        adapter Ticking of component receiver : Receiver {
                            clock ticker (rate = 1 s)
                            when ticker / run
                            queue messages (priority = 1, capacity = 1) {
                                ticker, signals.any
                            }
                        }
                        verification case Ticks of Ticking {
                            EF true
                        }
                    """,
                ),
                Arguments.of(
                    "the record-typed parameter of 'first' stored in the queue of statechart 'Receiver'",
                    """
                        package test
                        type Point : record {
                            x : integer
                        }
                        interface Signals {
                            in event first(position : Point)
                        }
                        @Asynchronous
                        statechart Receiver [
                            port signals : provides Signals
                        ] {
                        }
                    """,
                ),
            )
        }

        @JvmStatic
        fun refusedConstructs(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "the full-step control function",
                    """
                        package test
                        interface Signals {
                            in event first
                        }
                        statechart Receiver [
                            port signals : provides Signals
                        ] {
                        }
                        adapter Buffered of component receiver : Receiver {
                            when any / full-step
                            queue messages (priority = 1, capacity = 1) {
                                signals.any
                            }
                        }
                    """,
                ),
                Arguments.of(
                    "a transition from 'Right' to the history node 'History' from an orthogonal region of its parent state",
                    """
                        package test
                        interface Control {
                            in event start
                        }
                        statechart Machine [
                            port control : provides Control
                        ] {
                            region Main {
                                initial MainInitial
                                state Outer {
                                    region First {
                                        initial FirstInitial
                                        shallow history History
                                        state Left
                                    }
                                    region Second {
                                        initial SecondInitial
                                        state Right
                                    }
                                }
                            }
                            transition from MainInitial to Outer
                            transition from FirstInitial to Left
                            transition from History to Left
                            transition from SecondInitial to Right
                            transition from Right to History when control.start
                        }
                    """,
                ),
                Arguments.of(
                    "a channel from the instance 'machine' to itself",
                    """
                        package test
                        interface Control {
                            in event start
                        }
                        statechart Machine [
                            port input : provides Control,
                            port output : requires Control
                        ] {
                        }
                        sync System {
                            component machine : Machine
                            channel [ machine.input ] -o)- [ machine.output ]
                        }
                    """,
                ),
            )
        }
    }
}
