/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
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
        return GammaToOxstsSerializer().transformToOxsts(gammaPackage).lines().map {
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
            "package test",
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
            "class Control : Interface {",
            "contains start: Event subsets inputEvents {",
            "@Shared",
            "redefine contains declaration: EventDeclaration",
            "}",
            "contains done: Event subsets outputEvents {",
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
            "contains command: ProvidedPort subsets ports {",
            "redefine contains interface: Control",
            "}",
            "contains control: RequiredPort subsets ports {",
            "redefine contains interface: Control",
            "}",
            "}",
        )
    }

    @Test
    fun `a statechart without a region schedule is top-down as in Gamma`() {
        val lines = statechartLines("")

        assertThat(lines).containsSequence(
            "class Machine : Statechart {",
            "redefine refers regionSchedule: RegionSchedule = RegionSchedule::TopDown",
        )
    }

    @Test
    fun `a bottom-up region schedule redefines the statechart's schedule`() {
        val lines = statechartLines("", annotation = "@RegionSchedule = bottom-up")

        assertThat(lines).containsSequence(
            "class Machine : Statechart {",
            "redefine refers regionSchedule: RegionSchedule = RegionSchedule::BottomUp",
        )
    }

    @Test
    fun `a top-down region schedule redefines the statechart's schedule`() {
        val lines = statechartLines("", annotation = "@RegionSchedule = top-down")

        assertThat(lines).containsSequence(
            "class Machine : Statechart {",
            "redefine refers regionSchedule: RegionSchedule = RegionSchedule::TopDown",
        )
    }

    @Test
    fun `a variable is contained with its type and default value`() {
        val lines = statechartLines("")

        assertThat(lines).containsSequence(
            "contains count: IntegerVariable subsets variables {",
            "redefine refers defaultValue: int = 0",
            "}",
            "contains flag: BooleanVariable subsets variables {",
            "}",
        )
    }

    @Test
    fun `a timeout is contained as a Timeout`() {
        val lines = statechartLines("")

        assertThat(lines).contains("contains delay: Timeout subsets timeouts")
    }

    @Test
    fun `a region contains its states and its entry transition`() {
        val lines = statechartLines("")

        assertThat(lines).containsSequence(
            "contains Main: Region subsets regions {",
            "contains Idle: State subsets states {",
            "}",
            "contains Busy: State subsets states {",
            "}",
            "contains entry_to_Idle: EntryTransition subsets entryTransitions {",
            "redefine refers to: State = Idle",
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
            "contains Idle: State subsets states {",
            "contains entry_assign_count: AssignmentAction subsets entryActions {",
            "redefine refers variable: Variable = count",
            "redefine contains expression: LiteralIntegerExpression {",
            "redefine refers value: int = 1",
            "}",
            "}",
            "contains exit_raise_control_done: RaiseEventAction subsets exitActions {",
            "redefine refers port: Port = control",
            "redefine refers event: Event = control.interface.done",
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
            "contains Outer: State subsets states {",
            "contains Inner: Region subsets regions {",
            "contains Nested: State subsets states {",
            "}",
            "contains entry_to_Nested: EntryTransition subsets entryTransitions {",
            "redefine refers to: State = Nested",
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
            "contains Idle_to_Busy_on_control_start: Transition subsets transitions {",
            "contains Idle_to_Busy_on_delay: Transition subsets transitions {",
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
            "contains Idle_to_Busy: Transition subsets transitions {",
            "contains Idle_to_Busy_2: Transition subsets transitions {",
        )
    }

    @Test
    fun `a transition refers to its source and target states`() {
        val lines = statechartLines("transition from Idle to Busy")

        assertThat(lines).containsSequence(
            "contains Idle_to_Busy: Transition subsets transitions {",
            "redefine refers from: State = Idle",
            "redefine refers to: State = Busy",
            "}",
        )
    }

    @Test
    fun `an event trigger refers to the port and the event of its interface`() {
        val lines = statechartLines("transition from Idle to Busy when control.start")

        assertThat(lines).containsSequence(
            "redefine refers to: State = Busy",
            "redefine contains trigger: EventTrigger {",
            "redefine refers port: Port = control",
            "redefine refers event: Event = control.interface.start",
            "}",
        )
    }

    @Test
    fun `a timeout trigger refers to the timeout`() {
        val lines = statechartLines("transition from Busy to Idle when timeout delay")

        assertThat(lines).containsSequence(
            "redefine refers to: State = Idle",
            "redefine contains trigger: TimeoutTrigger {",
            "redefine refers timeout: Timeout = delay",
            "}",
        )
    }

    @Test
    fun `a guard contains its expression`() {
        val lines = statechartLines("transition from Idle to Busy [ count < 3 ]")

        assertThat(lines).containsSequence(
            "redefine refers to: State = Busy",
            "redefine contains guard: Guard {",
            "redefine contains expression: LessThanOperatorExpression {",
            "redefine contains left: VariableExpression {",
            "redefine refers variable: Variable = count",
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
            "redefine refers port: Port = control",
            "redefine refers event: Event = control.interface.done",
            "}",
        )
    }

    @Test
    fun `a set timeout action refers to the timeout and contains its value`() {
        val lines = statechartLines("transition from Idle to Busy / set delay := 2 s;")

        assertThat(lines).containsSequence(
            "contains set_delay: SetTimeoutAction subsets actions {",
            "redefine refers timeout: Timeout = delay",
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
            "redefine refers variable: Variable = flag",
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
            "redefine refers variable: Variable = count",
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
            "redefine refers variable: Variable = count",
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
            "redefine refers variable: Variable = flag",
            "}",
            "redefine contains right: UnaryNotExpression {",
            "redefine contains body: VariableExpression {",
            "redefine refers variable: Variable = flag",
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
            "contains leader: Leader subsets components",
            "contains worker: Worker subsets components",
        )
    }

    @Test
    fun `a sync component contains its unbound ports and refers to the instance port of each bound one`() {
        val lines = systemLines(system)

        assertThat(lines).containsSequence(
            "class System : SyncComponent {",
            "contains spare: ProvidedPort subsets ports {",
            "redefine contains interface: Control",
            "}",
            "refers control: Port subsets ports = leader.control",
        )
    }

    @Test
    fun `a channel connects the provided port to the required port`() {
        val lines = systemLines(system)

        assertThat(lines).containsSequence(
            "contains leader_workerControl_to_worker_control: Channel subsets channels {",
            "redefine refers inputPort: Port = leader.workerControl",
            "redefine refers outputPort: Port = worker.control",
            "}",
        )
    }

    @Test
    fun `an EF verification case redefines the component and contains an Eventually property`() {
        val lines = systemLines(
            system,
            """
                verification case LeaderIdleReachable {
                    component system : System
                    property {
                        EF state leader.Main.Idle
                    }
                }
            """,
        )

        assertThat(lines).containsSequence(
            "@VerificationCase",
            "class LeaderIdleReachable : GammaVerificationCase {",
            "contains system: System redefines component",
            "redefine contains propertyExpression: Eventually {",
            "redefine contains body: IsStateActiveExpression {",
            "redefine refers state: State = system.leader.Main.Idle",
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
                verification case LeaderCountStaysSmall {
                    component system : System
                    property {
                        AG variable leader.count < 2
                    }
                }
            """,
        )

        assertThat(lines).containsSequence(
            "redefine contains propertyExpression: MustAlways {",
            "redefine contains body: LessThanOperatorExpression {",
            "redefine contains left: VariableExpression {",
            "redefine refers variable: Variable = system.leader.count",
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
                verification case NestedReachable {
                    component system : System
                    property {
                        EF state machine.Inner.Nested
                    }
                }
            """,
        )

        assertThat(lines).contains("redefine refers state: State = system.machine.Main.Outer.Inner.Nested")
    }

    @Test
    fun `the actions of a block are serialized in order`() {
        val lines = statechartLines("transition from Idle to Busy when control.start / { count := 1; raise control.done; }")

        assertThat(lines).containsSubsequence(
            "contains assign_count: AssignmentAction subsets actions {",
            "contains raise_control_done: RaiseEventAction subsets actions {",
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
            GammaToOxstsSerializer().transformToOxsts(gammaPackage)
        }.isInstanceOf(UnmappedGammaConstructException::class.java).hasMessageContaining(construct).hasMessageContaining("is not mapped to OXSTS yet").hasMessageMatching("(?s).*:\\d+: .*")
    }

    companion object {
        private fun statechart(
            declarations: String = "",
            annotation: String = "",
            body: String = "",
        ): String {
            return """
                package test
                interface Control {
                    in event start
                }
                $annotation
                statechart Machine [
                    port control : provides Control
                ] {
                    $declarations
                    region Main {
                        initial MainInitial
                        state Idle
                        state Busy
                        $body
                    }
                    transition from MainInitial to Idle
                }
            """
        }

        @JvmStatic
        fun unmappedConstructs(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "the import of another package",
                    """
                        package test
                        import other
                    """,
                ),
                Arguments.of(
                    "the type declaration 'Point'",
                    """
                        package test
                        type Point : record {
                            x : integer
                        }
                    """,
                ),
                Arguments.of(
                    "the interface inheritance of 'Extended'",
                    """
                        package test
                        interface Base {
                            in event start
                        }
                        interface Extended extends Base {
                        }
                    """,
                ),
                Arguments.of(
                    "the inout event 'ping'",
                    """
                        package test
                        interface Control {
                            inout event ping
                        }
                    """,
                ),
                Arguments.of(
                    "the parameters of event 'start'",
                    """
                        package test
                        interface Control {
                            in event start(value : integer)
                        }
                    """,
                ),
                Arguments.of(
                    "the persistent event 'start'",
                    """
                        package test
                        interface Control {
                            in persistent event start
                        }
                    """,
                ),
                Arguments.of(
                    "the cascade composite 'System'",
                    """
                        package test
                        cascade System {
                        }
                    """,
                ),
                Arguments.of(
                    "the shallow history node 'History'",
                    statechart(body = "shallow history History"),
                ),
                Arguments.of(
                    "the transition priority order-based of 'Machine'",
                    statechart(annotation = "@TransitionPriority = order-based"),
                ),
                Arguments.of(
                    "the transition priority value-based of 'Machine'",
                    statechart(annotation = "@TransitionPriority = value-based"),
                ),
                Arguments.of(
                    "the guard evaluation beginning-of-step of 'Machine'",
                    statechart(annotation = "@GuardEvaluation = beginning-of-step"),
                ),
            )
        }
    }
}
