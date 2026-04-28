/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TemporalOperator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.eclipse.xtext.EcoreUtil2
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@InjectWithOxsts
class CompilationTests {
    @TempDir
    lateinit var tempDir: Path

    @Inject
    private lateinit var parseHelper: OxstsPackageParseHelper

    @Inject
    private lateinit var injector: Injector

    @Test
    fun `primitive int variable with assignment and EF property`() {
        assertCompiles(
            classToCompile = "Incrementing",
            source = """
                package compilation::tests::primitive_int
                @VerificationCase
                class Incrementing {
                    var x: int := 0
                    redefine init { x := 1 }
                    redefine tran { x := x + 1 }
                    prop { return EF x == 5 }
                }
            """,
        )
    }

    @Test
    fun `primitive bool variable with havoc`() {
        assertCompiles(
            classToCompile = "HavocBool",
            source = """
                package compilation::tests::bool_havoc
                @VerificationCase
                class HavocBool {
                    var b: bool := false
                    redefine tran { havoc(b) }
                    prop { return EF b == true }
                }
            """,
        )
    }

    @Test
    fun `choice operation with multiple branches`() {
        assertCompiles(
            classToCompile = "ChoiceStep",
            source = """
                package compilation::tests::branching
                @VerificationCase
                class ChoiceStep {
                    var x: int := 0
                    redefine tran {
                        choice {
                            x := 1
                        } or {
                            x := 2
                        } or {
                            x := 3
                        }
                    }
                    prop { return EF x == 2 }
                }
            """,
        )
    }

    @Test
    fun `if operation inside transition`() {
        assertCompiles(
            classToCompile = "IfStep",
            source = """
                package compilation::tests::if_in_tran
                @VerificationCase
                class IfStep {
                    var x: int := 0
                    redefine tran {
                        if (x < 3) {
                            x := x + 1
                        } else {
                            x := 0
                        }
                    }
                    prop { return EF x == 3 }
                }
            """,
        )
    }

    @Test
    fun `assume guard before write`() {
        assertCompiles(
            classToCompile = "GuardedWrite",
            source = """
                package compilation::tests::assumption
                @VerificationCase
                class GuardedWrite {
                    var x: int := 0
                    var guard: int := 0
                    redefine tran {
                        havoc(guard)
                        assume(guard > 0)
                        x := guard
                    }
                    prop { return EF x == 5 }
                }
            """,
        )
    }

    @Test
    fun `local variable declaration inside transition`() {
        assertCompiles(
            classToCompile = "LocalVar",
            source = """
                package compilation::tests::local_var
                @VerificationCase
                class LocalVar {
                    var x: int := 0
                    redefine tran {
                        var step: int := 2
                        x := x + step
                    }
                    prop { return EF x == 6 }
                }
            """,
        )
    }

    @Test
    fun `inline for over a range unrolls at compile time`() {
        assertCompiles(
            classToCompile = "InlineForSum",
            source = """
                package compilation::tests::inline_for
                @VerificationCase
                class InlineForSum {
                    var x: int := 0
                    redefine tran {
                        inline for (n in 1..3) {
                            x := x + n
                        }
                    }
                    prop { return EF x == 6 }
                }
            """,
        )
    }

    @Test
    fun `inline for choice over a range produces a choice`() {
        assertCompiles(
            classToCompile = "InlineForChoice",
            source = """
                package compilation::tests::inline_for_choice
                @VerificationCase
                class InlineForChoice {
                    var x: int := 0
                    redefine tran {
                        inline for choice (n in 1..3) {
                            x := n
                        }
                    }
                    prop { return EF x == 2 }
                }
            """,
        )
    }

    @Test
    fun `inline if with static guard`() {
        assertCompiles(
            classToCompile = "InlineIf",
            source = """
                package compilation::tests::inline_if
                @VerificationCase
                class InlineIf {
                    var x: int := 0
                    redefine tran {
                        inline if (1 < 2) {
                            x := 1
                        } else {
                            x := -1
                        }
                    }
                    prop { return EF x == 1 }
                }
            """,
        )
    }

    @Test
    fun `parametric transition called inline`() {
        assertCompiles(
            classToCompile = "ParametricInline",
            source = """
                package compilation::tests::parametric_inline
                @VerificationCase
                class ParametricInline {
                    var x: int := 0

                    tran addBy(n: int) {
                        x := x + n
                    }

                    redefine tran {
                        inline addBy(2)
                    }

                    prop { return EF x == 6 }
                }
            """,
        )
    }

    @Test
    fun `inheritance with redefine of tran`() {
        assertCompiles(
            classToCompile = "Child",
            source = """
                package compilation::tests::inheritance
                class Base {
                    var x: int := 0
                    redefine tran { x := x + 1 }
                }

                @VerificationCase
                class Child : Base {
                    redefine tran { x := x + 2 }
                    prop { return EF x == 6 }
                }
            """,
        )
    }

    @Test
    fun `abstract class with concrete subclass`() {
        assertCompiles(
            classToCompile = "Concrete",
            source = """
                package compilation::tests::abstract_concrete
                abstract class Base {
                    var x: int := 0
                    abstract tran step()
                }

                @VerificationCase
                class Concrete : Base {
                    redefine tran step() { x := x + 1 }
                    redefine tran { inline step() }
                    prop { return EF x == 3 }
                }
            """,
        )
    }

    @Test
    fun `containment feature with required multiplicity`() {
        assertCompiles(
            classToCompile = "Parent",
            source = """
                package compilation::tests::containment
                class Leaf {
                    var v: int := 0
                    redefine tran { v := v + 1 }
                }

                @VerificationCase
                class Parent {
                    contains leaf: Leaf[1]
                    redefine tran { inline leaf.main() }
                    prop { return EF leaf.v == 3 }
                }
            """,
        )
    }

    @Test
    fun `optional containment feature holds nothing`() {
        assertCompiles(
            classToCompile = "Parent",
            source = """
                package compilation::tests::optional_containment
                class Leaf {
                    var v: int := 0
                }

                @VerificationCase
                class Parent {
                    contains leaf: Leaf[0..1]
                    var slot: leaf[0..1] := nothing
                    redefine tran {
                        havoc(slot)
                    }
                    prop { return EF slot == nothing }
                }
            """,
        )
    }

    @Test
    fun `opposite containment and container features`() {
        assertCompiles(
            classToCompile = "Parent",
            source = """
                package compilation::tests::bidirectional
                class Child {
                    container parent: Parent opposite children
                    var v: int := 0
                    redefine tran { v := v + 1 }
                }

                @VerificationCase
                class Parent {
                    contains children: Child[1..*] opposite parent
                    contains only: Child[1] subsets children

                    redefine tran { inline only.main() }
                    prop { return EF only.v == 2 }
                }
            """,
        )
    }

    @Test
    fun `enum declaration and literal comparison`() {
        assertCompiles(
            classToCompile = "LightCycle",
            source = """
                package compilation::tests::enums
                enum Color { Red, Green, Blue }

                @VerificationCase
                class LightCycle {
                    var color: Color := Color::Red
                    redefine tran {
                        choice {
                            assume(color == Color::Red)
                            color := Color::Green
                        } or {
                            assume(color == Color::Green)
                            color := Color::Blue
                        } or {
                            assume(color == Color::Blue)
                            color := Color::Red
                        }
                    }
                    prop { return EF color == Color::Blue }
                }
            """,
        )
    }

    @Test
    fun `AG safety property with boolean operators`() {
        assertCompiles(
            classToCompile = "Safety",
            source = """
                package compilation::tests::ag_safety
                @VerificationCase
                class Safety {
                    var a: int := 0
                    var b: int := 0
                    redefine tran {
                        choice { a := 1 } or { b := 1 }
                    }
                    prop { return AG !(a == 1 && b == 1) }
                }
            """,
        )
    }

    @Test
    fun `redefine refers with instance expression`() {
        assertCompiles(
            classToCompile = "Wiring",
            source = """
                package compilation::tests::refers_instance
                class Node {
                    refers peer: Node
                    var v: int := 0
                    redefine tran { v := peer.v + 1 }
                }

                @VerificationCase
                class Wiring {
                    contains a: Node[1] {
                        redefine refers peer: Node[1] = b
                    }
                    contains b: Node[1] {
                        redefine refers peer: Node[1] = a
                    }
                    redefine tran {
                        inline a.main()
                    }
                    prop { return EF a.v == 1 }
                }
            """,
        )
    }

    @Test
    fun `parametric property with parameter`() {
        assertCompiles(
            classToCompile = "ParametricProp",
            source = """
                package compilation::tests::parametric_prop
                @VerificationCase
                class ParametricProp {
                    var x: int := 0
                    redefine tran { x := x + 1 }

                    prop equals(n: int): bool {
                        return x == n
                    }

                    prop { return EF equals(3) }
                }
            """,
        )
    }

    @Test
    fun `nested feature navigation through multiple containments`() {
        assertCompiles(
            classToCompile = "Outer",
            source = """
                package compilation::tests::nested_nav
                class Deep {
                    var v: int := 0
                    tran step() { v := v + 1 }
                }
                class Middle {
                    contains deep: Deep[1]
                    tran step() { inline deep.step() }
                }

                @VerificationCase
                class Outer {
                    contains middle: Middle[1]
                    redefine tran { inline middle.step() }
                    prop { return EF middle.deep.v == 2 }
                }
            """,
        )
    }

    @Test
    fun `negation and unary minus operators`() {
        assertCompiles(
            classToCompile = "Negations",
            source = """
                package compilation::tests::negations
                @VerificationCase
                class Negations {
                    var b: bool := true
                    var n: int := 1
                    redefine tran {
                        b := !b
                        n := -n
                    }
                    prop { return EF (!b && n == -1) }
                }
            """,
        )
    }

    @Test
    fun `multiple init branches via or`() {
        assertCompiles(
            classToCompile = "ChoicyInit",
            source = """
                package compilation::tests::multi_init
                @VerificationCase
                class ChoicyInit {
                    var x: int := 0
                    redefine init {
                        x := 1
                    } or {
                        x := 2
                    } or {
                        x := 3
                    }
                    redefine tran { }
                    prop { return EF x == 2 }
                }
            """,
        )
    }

    @Test
    fun `trivial property without explicit temporal operator`() {
        assertCompiles(
            classToCompile = "Trivial",
            source = """
                package compilation::tests::trivial_prop
                @VerificationCase
                class Trivial {
                    var x: int := 0
                    redefine tran { x := x + 1 }
                    prop { return true }
                }
            """,
        )
    }

    @Test
    fun `variable without explicit initializer uses default`() {
        assertCompiles(
            classToCompile = "NoInit",
            source = """
                package compilation::tests::no_init
                @VerificationCase
                class NoInit {
                    var x: int
                    redefine tran { x := x + 1 }
                    prop { return EF x > 0 }
                }
            """,
        )
    }

    @Test
    fun `self reference resolves to current instance`() {
        assertCompiles(
            classToCompile = "SelfUse",
            source = """
                package compilation::tests::self_use
                @VerificationCase
                class SelfUse {
                    var x: int := 0
                    tran step() { self.x := self.x + 1 }
                    redefine tran { inline self.step() }
                    prop { return EF self.x == 3 }
                }
            """,
        )
    }

    @Test
    fun `integer comparisons spanning ordering operators`() {
        assertCompiles(
            classToCompile = "Ordering",
            source = """
                package compilation::tests::ordering
                @VerificationCase
                class Ordering {
                    var x: int := 0
                    redefine tran {
                        choice { x := x + 1 } or { x := x - 1 }
                    }
                    prop { return AG (x >= -10 && x <= 10 && !(x < -100) && !(x > 100)) }
                }
            """,
        )
    }

    @Test
    fun `inline transition call through a feature-typed variable dispatches across candidates`() {
        assertCompiles(
            classToCompile = "Dispatcher",
            source = """
                package compilation::tests::variable_dispatch
                class Worker {
                    var done: bool := false
                    tran step() { done := true }
                }

                @VerificationCase
                class Dispatcher {
                    contains workers: Worker[0..*]
                    contains a: Worker[1] subsets workers
                    contains b: Worker[1] subsets workers
                    var current: workers[0..1] := nothing
                    redefine init {
                        choice { current := a } or { current := b }
                    }
                    redefine tran {
                        inline current.step()
                    }
                    prop { return EF (a.done && b.done) }
                }
            """,
        )
    }

    @Test
    fun `derived refers feature with opposite resolves via reverse lookup`() {
        assertCompiles(
            classToCompile = "Wiring",
            source = """
                package compilation::tests::derived_refers
                class Node {
                    refers ownedBy: Graph[1]
                    var v: int := 0
                }

                @VerificationCase
                class Wiring {
                    contains graph: Graph[1]
                    contains n: Node[1] {
                        redefine refers ownedBy: Graph[1] = graph
                    }
                    redefine tran { n.v := n.v + 1 }
                    prop { return EF n.v == 1 }
                }

                class Graph {
                    derived refers owns: Node[0..*] opposite ownedBy
                }
            """,
        )
    }

    @Test
    fun `global containment feature at package level`() {
        assertCompiles(
            classToCompile = "Consumer",
            source = """
                package compilation::tests::global_containment
                class Shared {
                    var flag: bool := false
                }

                global containment sharedInstance: Shared[1]

                @VerificationCase
                class Consumer {
                    redefine tran { sharedInstance.flag := true }
                    prop { return EF sharedInstance.flag }
                }
            """,
        )
    }

    @Test
    fun `abstract property with concrete redefinition`() {
        assertCompiles(
            classToCompile = "Concrete",
            source = """
                package compilation::tests::abstract_prop
                abstract class Base {
                    var x: int := 0
                    abstract prop predicate(): bool
                }

                @VerificationCase
                class Concrete : Base {
                    redefine prop predicate(): bool {
                        return x > 0
                    }
                    redefine tran { x := x + 1 }
                    prop { return EF predicate() }
                }
            """,
        )
    }

    @Test
    fun `optional navigation on empty containment drops the call`() {
        assertCompiles(
            classToCompile = "Parent",
            source = """
                package compilation::tests::optional_nav
                class Leaf {
                    var v: int := 0
                    tran bump() { v := v + 1 }
                }

                @VerificationCase
                class Parent {
                    contains leaf: Leaf[0..1]
                    redefine tran { inline leaf?.bump() }
                    prop { return AG true }
                }
            """,
        )
    }

    @Test
    fun `nested negation and boolean combinators in property`() {
        assertCompiles(
            classToCompile = "Nested",
            source = """
                package compilation::tests::nested_negation
                @VerificationCase
                class Nested {
                    var a: bool := false
                    var b: bool := false
                    redefine tran {
                        choice { a := !a } or { b := !b }
                    }
                    prop { return AG !(!(a || b) && (a || b)) }
                }
            """,
        )
    }

    @Test
    fun `parametric transition called with a non-literal argument`() {
        assertCompiles(
            classToCompile = "ComputedArg",
            source = """
                package compilation::tests::non_literal_arg
                @VerificationCase
                class ComputedArg {
                    var x: int := 0
                    var base: int := 3

                    tran addBy(n: int) {
                        x := x + n
                    }

                    redefine tran {
                        inline addBy(base + 2)
                    }

                    prop { return EF x == 5 }
                }
            """,
        )
    }

    @Test
    fun `parametric transition called with an enum argument`() {
        assertCompiles(
            classToCompile = "Dispatcher",
            source = """
                package compilation::tests::enum_arg
                enum Command { Inc, Dec }

                @VerificationCase
                class Dispatcher {
                    var x: int := 0

                    tran apply(cmd: Command) {
                        choice {
                            assume(cmd == Command::Inc)
                            x := x + 1
                        } or {
                            assume(cmd == Command::Dec)
                            x := x - 1
                        }
                    }

                    redefine tran {
                        inline apply(Command::Inc)
                    }

                    prop { return EF x == 3 }
                }
            """,
        )
    }

    @Test
    fun `non-inline for over an instance range iterates at runtime`() {
        assertCompiles(
            classToCompile = "Host",
            source = """
                package compilation::tests::runtime_for
                class Worker {
                    var v: int := 0
                    tran bump() { v := v + 1 }
                }

                @VerificationCase
                class Host {
                    contains workers: Worker[0..*]
                    contains a: Worker[1] subsets workers
                    contains b: Worker[1] subsets workers
                    redefine tran {
                        for (w in workers) {
                            inline w.bump()
                        }
                    }
                    prop { return EF (a.v == 1 && b.v == 1) }
                }
            """,
        )
    }

    @Test
    fun `fixed-size integer array with array literal initializer compiles`() {
        assertCompiles(
            classToCompile = "ArrayHolder",
            source = """
                package compilation::tests::array_fixed
                @VerificationCase
                class ArrayHolder {
                    var slots: int[3] := [10, 20, 30]
                    redefine tran {
                        slots[0] := slots[0] + 1
                    }
                    prop { return EF slots[0] == 11 }
                }
            """,
        )
    }

    @Test
    fun `array with constant index reads and writes compiles`() {
        assertCompiles(
            classToCompile = "ArrayAccess",
            source = """
                package compilation::tests::array_access
                @VerificationCase
                class ArrayAccess {
                    var values: int[4] := [0, 0, 0, 0]
                    redefine tran {
                        values[0] := values[1] + values[2]
                    }
                    prop { return AG values[3] == 0 }
                }
            """,
        )
    }

    @Test
    fun `real literal passes through the compiler uninterpreted`() {
        // Pins current behaviour: real literals parse and survive the
        // compilation pipeline even though Theta's backend only supports
        // int/bool/enum. Semantic correctness at the backend level belongs
        // to the verification suite; if real-literal support is added or
        // rejected downstream, this test documents the change.
        assertCompiles(
            classToCompile = "RealLiteral",
            source = """
                package compilation::tests::real_literal
                @VerificationCase
                class RealLiteral {
                    var x: int := 0
                    redefine tran { x := x + 1 }
                    prop { return EF x > 3.14 }
                }
            """,
        )
    }

    @Test
    fun `string literal passes through the compiler uninterpreted`() {
        // Pins current behaviour: see note on real literals.
        assertCompiles(
            classToCompile = "StringLiteral",
            source = """
                package compilation::tests::string_literal
                @VerificationCase
                class StringLiteral {
                    var x: int := 0
                    redefine tran { x := x + 1 }
                    prop { return EF x == "hi" }
                }
            """,
        )
    }

    @Test
    fun `array with runtime index reads and writes compiles`() {
        // Runtime-index access was previously rejected; ArrayFlatteningPass
        // now rewrites it to an if-chain over the per-slot variables.
        assertCompiles(
            classToCompile = "RuntimeArray",
            source = """
                package compilation::tests::runtime_array
                @VerificationCase
                class RuntimeArray {
                    var values: int[3] := [0, 0, 0]
                    var idx: int := 0
                    var seen: int := 0
                    redefine tran {
                        havoc(idx)
                        assume(idx >= 0 && idx < 3)
                        values[idx] := values[idx] + 1
                        seen := values[idx]
                    }
                    prop { return EF seen == 1 }
                }
            """,
        )
    }

    @Test
    fun `array literal indexing is rejected`() {
        assertCompilationFails(
            classToCompile = "Indexing",
            source = """
                package compilation::tests::indexing
                @VerificationCase
                class Indexing {
                    var x: int := 0
                    redefine tran {
                        x := [1, 2, 3][1]
                    }
                    prop { return EF x == 2 }
                }
            """,
        )
    }

    @Test
    fun `non-inline for over an integer range iterates at runtime`() {
        assertCompiles(
            classToCompile = "RuntimeFor",
            source = """
                package compilation::tests::runtime_for_int
                @VerificationCase
                class RuntimeFor {
                    var total: int := 0
                    redefine tran {
                        for (n in 1..3) {
                            total := total + n
                        }
                    }
                    prop { return EF total == 6 }
                }
            """,
        )
    }

    @Test
    fun `abstract features kind is compiled when subsetters provide instances`() {
        assertCompiles(
            classToCompile = "Host",
            source = """
                package compilation::tests::features_kind
                class Sensor {
                    var fired: bool := false
                }

                @VerificationCase
                class Host {
                    features sensors: Sensor
                    contains s1: Sensor[1] subsets sensors
                    redefine tran { s1.fired := true }
                    prop { return EF s1.fired }
                }
            """,
        )
    }

    @Test
    fun `if-then-else expression in assignment compiles`() {
        assertCompiles(
            classToCompile = "IfThenElseAssign",
            source = """
                package compilation::tests::ite_assign
                @VerificationCase
                class IfThenElseAssign {
                    var n: int := 0
                    var result: int := 0
                    redefine tran {
                        n := n + 1
                        result := if n > 0 then 1 else -1
                    }
                    prop { return EF result == 1 }
                }
            """,
        )
    }

    @Test
    fun `if-then-else expression inside a temporal property compiles`() {
        assertCompiles(
            classToCompile = "IfThenElseProp",
            source = """
                package compilation::tests::ite_prop
                @VerificationCase
                class IfThenElseProp {
                    var n: int := 0
                    redefine tran { n := n + 1 }
                    prop { return EF (if n > 0 then true else false) }
                }
            """,
        )
    }

    @Test
    fun `cast expression narrows a base-typed reference to its child`() {
        // Narrowing downcast: `ref` statically typed as Base is cast to Child
        // where the programmer knows the concrete instance is a Child.
        assertCompiles(
            classToCompile = "CastNarrow",
            source = """
                package compilation::tests::cast_narrow
                class Base { }
                class Child : Base { }
                @VerificationCase
                class CastNarrow {
                    contains children: Base[0..*]
                    contains child: Child[1] subsets children
                    var ref: children[0..1] := nothing
                    redefine init { ref := child }
                    redefine tran {
                        var c: Child[0..1] := nothing
                        c := ref as Child
                    }
                    prop { return EF ref == child }
                }
            """,
        )
    }

    @Test
    fun `two verification cases in one package`() {
        assertCompiles(
            classToCompile = "SecondCase",
            source = """
                package compilation::tests::multi_case
                @VerificationCase
                class FirstCase {
                    var x: int := 0
                    redefine tran { x := x + 1 }
                    prop { return EF x == 1 }
                }

                @VerificationCase
                class SecondCase {
                    var y: int := 10
                    redefine tran { y := y - 1 }
                    prop { return EF y == 5 }
                }
            """,
        )
    }

    /**
     * Assert the pipeline rejects this source: either parsing errors, or
     * the compiler itself throws. [messageContains] is an optional substring
     * filter on the reported diagnostics / exception; pass "" to accept any
     * rejection. Useful for pinning that the compiler says "nope" to a given
     * language feature, so regressions that silently accept it are caught.
     */
    private fun assertCompilationFails(
        classToCompile: String,
        source: String,
        messageContains: String = "",
    ) {
        val parsed = try {
            parseHelper.parse(source.trimIndent())
        } catch (e: Exception) {
            if (messageContains.isNotEmpty()) {
                assertThat(e.message).asString().containsIgnoringCase(messageContains)
            }
            return
        }

        if (parsed.resourceErrors.isNotEmpty()) {
            if (messageContains.isNotEmpty()) {
                val joined = parsed.resourceErrors.joinToString("\n") { it.message ?: "" }
                assertThat(joined).containsIgnoringCase(messageContains)
            }
            return
        }

        val classDecl = EcoreUtil2
            .eAllOfType(parsed.oxstsPackage, ClassDeclaration::class.java)
            .singleOrNull { it.name == classToCompile }
            ?: error("Class '$classToCompile' not found in the test model")

        val artifactDir = tempDir.resolve("artifacts").also {
            Files.createDirectories(it)
        }
        val thrown = catchThrowable {
            SemantifyrCompiler(
                injector = injector,
                artifactConfig = ArtifactConfig.ALL,
            ).use {
                it.compile(classDecl, artifactDir)
            }
        }

        assertThat(thrown)
            .`as`("compilation of '$classToCompile' was expected to fail")
            .isNotNull()
        if (messageContains.isNotEmpty()) {
            assertThat(thrown!!.message ?: "").containsIgnoringCase(messageContains)
        }
    }

    private fun assertCompiles(
        classToCompile: String,
        source: String,
    ) {
        val parsed = parseHelper.parse(source.trimIndent())
        if (parsed.resourceErrors.isNotEmpty()) {
            val formatted = parsed.resourceErrors.joinToString("\n") {
                "  ${it.location ?: "<unknown>"}:${it.line}:${it.column}: ${it.message}"
            }
            error("Test fixture failed to parse:\n$formatted")
        }

        val classDecl = EcoreUtil2
            .eAllOfType(parsed.oxstsPackage, ClassDeclaration::class.java)
            .singleOrNull { it.name == classToCompile }
            ?: error("Class '$classToCompile' not found in the test model")

        val artifactDir = tempDir.resolve("artifacts").also {
            Files.createDirectories(it)
        }
        val compiled = SemantifyrCompiler(
            injector = injector,
            artifactConfig = ArtifactConfig.ALL,
        ).use {
            it.compile(classDecl, artifactDir)
        }

        assertThat(compiled.inlinedOxsts.classDeclaration).isNotNull
        assertThat(compiled.flatteningInfo).isNotNull
        assertFlattenedContract(compiled)

        val emitted = artifactDir.toFile().walkTopDown().filter {
            it.isFile
        }.map {
            it.name
        }.toSet()
        assertThat(emitted)
            .`as`("default config should emit the three staged model artifacts")
            .contains("instantiated.oxsts", "inlined.oxsts", "flattened.oxsts")
    }

    private fun assertFlattenedContract(compiled: FlattenedCompilationContext) {
        val inlined = compiled.inlinedOxsts

        assertThat(inlined.rootFeature)
            .`as`("post-flatten IR must have no rootFeature")
            .isNull()

        val transitions = listOfNotNull(inlined.initTransition, inlined.mainTransition)
        val property: PropertyDeclaration = requireNotNull(inlined.property) {
            "post-flatten IR must have a property declaration"
        }
        assertThat(inlined.initTransition).`as`("init transition is present").isNotNull
        assertThat(inlined.mainTransition).`as`("main transition is present").isNotNull

        val reachableRoots = transitions + property

        val remainingNothing = reachableRoots.flatMap {
            EcoreUtil2.eAllOfType(it, LiteralNothing::class.java)
        }
        assertThat(remainingNothing)
            .`as`("post-flatten IR must not contain LiteralNothing (encoded as -1)")
            .isEmpty()

        val featureNavigations = reachableRoots.flatMap {
            EcoreUtil2.eAllOfType(it, NavigationSuffixExpression::class.java)
        }.filter {
            it.member is FeatureDeclaration
        }
        assertThat(featureNavigations)
            .`as`("post-flatten IR must not navigate through feature members")
            .isEmpty()

        for (variable in inlined.variables) {
            val domain = variable.typeSpecification?.domain
            assertThat(domain)
                .`as`("variable '${variable.name}' domain after flatten")
                .isNotNull
                .isInstanceOfAny(DataTypeDeclaration::class.java, EnumDeclaration::class.java)
        }

        val propertyExpression = property.expression
        assertThat(propertyExpression)
            .`as`("property expression must be a temporal operator or a literal boolean after optimization")
            .isNotNull
            .satisfiesAnyOf(
                {
                    assertThat(it).isInstanceOf(TemporalOperator::class.java)
                },
                {
                    assertThat(it).isInstanceOf(LiteralBoolean::class.java)
                },
            )

        val declaredVariables = inlined.variables.toSet()
        for (holder in compiled.flatteningInfo.variableHolders.keys) {
            assertThat(holder in declaredVariables)
                .`as`("variableHolders key '${holder.name}' must match a surviving variable in inlinedOxsts.variables")
                .isTrue()
        }

        assertThat(compiled.flatteningInfo.instanceIdMapping)
            .`as`("flatteningInfo must carry an instance-id mapping (even if empty)")
            .isNotNull
    }
}
