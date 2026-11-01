/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.syntax;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FieldAssignment;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInfinity;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordLiteral;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class LiteralSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void booleanTrueLiteral() {
        var pkg = parseHelper.parse("""
            package test
            class C { var b: bool := true }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralBoolean) pkg.classByName("C").variableByName("b").initializer();
        assertThat(init.isValue()).isTrue();
    }

    @Test
    void booleanFalseLiteral() {
        var pkg = parseHelper.parse("""
            package test
            class C { var b: bool := false }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralBoolean) pkg.classByName("C").variableByName("b").initializer();
        assertThat(init.isValue()).isFalse();
    }

    @Test
    void integerLiteralPositive() {
        var pkg = parseHelper.parse("""
            package test
            class C { var n: int := 42 }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralInteger) pkg.classByName("C").variableByName("n").initializer();
        assertThat(init.getValue()).isEqualTo(42);
    }

    @Test
    void integerLiteralZero() {
        var pkg = parseHelper.parse("""
            package test
            class C { var n: int := 0 }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralInteger) pkg.classByName("C").variableByName("n").initializer();
        assertThat(init.getValue()).isEqualTo(0);
    }

    @Test
    void realLiteral() {
        var pkg = parseHelper.parse("""
            package test
            class C { var r: real := 3.14 }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralReal) pkg.classByName("C").variableByName("r").initializer();
        assertThat(init.getValue()).isEqualTo(3.14);
    }

    @Test
    void stringLiteral() {
        var pkg = parseHelper.parse("""
            package test
            class C { var s: string := "hello" }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralString) pkg.classByName("C").variableByName("s").initializer();
        assertThat(init.getValue()).isEqualTo("hello");
    }

    @Test
    void nothingLiteral() {
        var pkg = parseHelper.parse("""
            package test
            class Leaf { }
            class C {
                contains x: Leaf[0..1]
                var slot: x[0..1] := nothing
            }
            """);
        pkg.assertNoResourceErrors();
        var init = pkg.classByName("C").variableByName("slot").initializer();
        assertThat(init).isInstanceOf(LiteralNothing.class);
    }

    @Test
    void infinityLiteralInRange() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                redefine tran {
                    inline for (n in 0..*) { total := total + 1 }
                }
            }
            """);
        pkg.assertNoResourceErrors();
        var infinities =
                EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), LiteralInfinity.class);
        assertThat(infinities).hasSize(1);
    }

    @Test
    void arrayLiteralEmpty() {
        var pkg = parseHelper.parse("""
            package test
            class C { var empty: int[0] := [] }
            """);
        pkg.assertNoResourceErrors();
        var init = (ArrayLiteral) pkg.classByName("C").variableByName("empty").initializer();
        assertThat(init.getValues()).isEmpty();
    }

    @Test
    void arrayLiteralSingleElement() {
        var pkg = parseHelper.parse("""
            package test
            class C { var one: int[1] := [42] }
            """);
        pkg.assertNoResourceErrors();
        var init = (ArrayLiteral) pkg.classByName("C").variableByName("one").initializer();
        assertThat(init.getValues()).hasSize(1);
        assertThat(((LiteralInteger) init.getValues().getFirst()).getValue()).isEqualTo(42);
    }

    @Test
    void arrayLiteralMultipleElements() {
        var pkg = parseHelper.parse("""
            package test
            class C { var three: int[3] := [1, 2, 3] }
            """);
        pkg.assertNoResourceErrors();
        var init = (ArrayLiteral) pkg.classByName("C").variableByName("three").initializer();
        assertThat(init.getValues()).hasSize(3);
        assertThat(init.getValues().stream().map(e -> ((LiteralInteger) e).getValue()))
                .containsExactly(1, 2, 3);
    }

    private static final String RECORDS = """
            package test
            record Point {
                var x: int
                var y: int := 0
            }
            record Line {
                var from: Point
                var to: Point
            }
            """;

    @Test
    void recordLiteral() {
        var pkg = parseHelper.parse(RECORDS + """
            class C { var p: Point := Point { x = 1, y = 2 } }
            """);
        pkg.assertNoResourceErrors();
        var init = (RecordLiteral) pkg.classByName("C").variableByName("p").initializer();
        assertThat(init.getRecord().getName()).isEqualTo("Point");
        assertThat(init.getFields()).extracting(f -> f.getField().getName()).containsExactly("x", "y");
        assertThat(init.getFields())
                .extracting(f -> ((LiteralInteger) f.getExpression()).getValue())
                .containsExactly(1, 2);
    }

    @Test
    void recordLiteralWithQualifiedRecordAndTrailingComma() {
        var pkg = parseHelper.parse(RECORDS + """
            class C { var p: Point := test::Point { y = 2, } }
            """);
        pkg.assertNoResourceErrors();
        var init = (RecordLiteral) pkg.classByName("C").variableByName("p").initializer();
        assertThat(init.getRecord().getName()).isEqualTo("Point");
        assertThat(init.getFields()).extracting(f -> f.getField().getName()).containsExactly("y");
    }

    @Test
    void nestedRecordLiteral() {
        var pkg = parseHelper.parse(RECORDS + """
            class C { var l: Line := Line { from = Point { x = 1 }, to = Point { x = 2, y = 3 } } }
            """);
        pkg.assertNoResourceErrors();
        var init = (RecordLiteral) pkg.classByName("C").variableByName("l").initializer();
        assertThat(init.getFields()).extracting(f -> f.getField().getName()).containsExactly("from", "to");
        var from = (RecordLiteral) init.getFields().getFirst().getExpression();
        assertThat(from.getRecord().getName()).isEqualTo("Point");
        assertThat(((RecordDeclaration) from.getFields().getFirst().getField().eContainer()).getName())
                .isEqualTo("Point");
    }

    @Test
    void assignmentFollowedBySequenceIsNoRecordLiteral() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                var q: Point
                var n: int := 0
                redefine tran { p := q { n := 1 } }
            }
            """);
        pkg.assertNoResourceErrors();
        var steps = pkg.classByName("C").anonymousMain().branches().getFirst().getSteps();
        assertThat(steps).hasSize(2);
        assertThat(((AssignmentOperation) steps.get(0)).getExpression()).isInstanceOf(ElementReference.class);
        assertThat(steps.get(1)).isInstanceOf(SequenceOperation.class);
    }

    @Test
    void recordNameWithEmptyBracesIsNoRecordLiteral() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                redefine tran { p := Point {} }
            }
            """);
        pkg.assertNoResourceErrors();
        var main = pkg.classByName("C").anonymousMain().eObject();
        assertThat(EcoreUtil2.eAllOfType(main, RecordLiteral.class)).isEmpty();
        assertThat(EcoreUtil2.eAllOfType(main, FieldAssignment.class)).isEmpty();
    }
}
