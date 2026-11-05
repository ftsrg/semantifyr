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
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var flag: bool := true }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (LiteralBoolean)
                parsedPackage.classByName("Host").variableByName("flag").initializer();
        assertThat(initializer.isValue()).isTrue();
    }

    @Test
    void booleanFalseLiteral() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var flag: bool := false }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (LiteralBoolean)
                parsedPackage.classByName("Host").variableByName("flag").initializer();
        assertThat(initializer.isValue()).isFalse();
    }

    @Test
    void integerLiteralPositive() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var number: int := 42 }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (LiteralInteger)
                parsedPackage.classByName("Host").variableByName("number").initializer();
        assertThat(initializer.getValue()).isEqualTo(42);
    }

    @Test
    void integerLiteralZero() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var number: int := 0 }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (LiteralInteger)
                parsedPackage.classByName("Host").variableByName("number").initializer();
        assertThat(initializer.getValue()).isEqualTo(0);
    }

    @Test
    void realLiteral() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var measurement: real := 3.14 }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (LiteralReal)
                parsedPackage.classByName("Host").variableByName("measurement").initializer();
        assertThat(initializer.getValue()).isEqualTo(3.14);
    }

    @Test
    void realLiteralWithExponent() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var measurement: real := 1e-3 }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (LiteralReal)
                parsedPackage.classByName("Host").variableByName("measurement").initializer();
        assertThat(initializer.getValue()).isEqualTo(0.001);
    }

    @Test
    void stringLiteral() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var text: string := "hello" }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (LiteralString)
                parsedPackage.classByName("Host").variableByName("text").initializer();
        assertThat(initializer.getValue()).isEqualTo("hello");
    }

    @Test
    void nothingLiteral() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf { }
            class Host {
                contains leaf: Leaf[0..1]
                var slot: leaf[0..1] := nothing
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer =
                parsedPackage.classByName("Host").variableByName("slot").initializer();
        assertThat(initializer).isInstanceOf(LiteralNothing.class);
    }

    @Test
    void infinityLiteralInRange() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var total: int := 0
                redefine tran {
                    inline for (index in 0..*) { total := total + 1 }
                }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var infinities = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), LiteralInfinity.class);
        assertThat(infinities).hasSize(1);
    }

    @Test
    void arrayLiteralEmpty() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var empty: int[0] := [] }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (ArrayLiteral)
                parsedPackage.classByName("Host").variableByName("empty").initializer();
        assertThat(initializer.getValues()).isEmpty();
    }

    @Test
    void arrayLiteralSingleElement() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var one: int[1] := [42] }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (ArrayLiteral)
                parsedPackage.classByName("Host").variableByName("one").initializer();
        assertThat(initializer.getValues()).hasSize(1);
        assertThat(((LiteralInteger) initializer.getValues().getFirst()).getValue())
                .isEqualTo(42);
    }

    @Test
    void arrayLiteralMultipleElements() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var three: int[3] := [1, 2, 3] }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (ArrayLiteral)
                parsedPackage.classByName("Host").variableByName("three").initializer();
        assertThat(initializer.getValues()).hasSize(3);
        assertThat(initializer.getValues().stream().map(value -> ((LiteralInteger) value).getValue()))
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
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Host { var point: Point := Point { x = 1, y = 2 } }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (RecordLiteral)
                parsedPackage.classByName("Host").variableByName("point").initializer();
        assertThat(initializer.getRecord().getName()).isEqualTo("Point");
        assertThat(initializer.getFields())
                .extracting(assignment -> assignment.getField().getName())
                .containsExactly("x", "y");
        assertThat(initializer.getFields())
                .extracting(assignment -> ((LiteralInteger) assignment.getExpression()).getValue())
                .containsExactly(1, 2);
    }

    @Test
    void recordLiteralWithQualifiedRecordAndTrailingComma() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Host { var point: Point := test::Point { y = 2, } }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (RecordLiteral)
                parsedPackage.classByName("Host").variableByName("point").initializer();
        assertThat(initializer.getRecord().getName()).isEqualTo("Point");
        assertThat(initializer.getFields())
                .extracting(assignment -> assignment.getField().getName())
                .containsExactly("y");
    }

    @Test
    void nestedRecordLiteral() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Host { var line: Line := Line { from = Point { x = 1 }, to = Point { x = 2, y = 3 } } }
            """);
        parsedPackage.assertNoResourceErrors();
        var initializer = (RecordLiteral)
                parsedPackage.classByName("Host").variableByName("line").initializer();
        assertThat(initializer.getFields())
                .extracting(assignment -> assignment.getField().getName())
                .containsExactly("from", "to");
        var from = (RecordLiteral) initializer.getFields().getFirst().getExpression();
        assertThat(from.getRecord().getName()).isEqualTo("Point");
        assertThat(((RecordDeclaration) from.getFields().getFirst().getField().eContainer()).getName())
                .isEqualTo("Point");
    }

    @Test
    void assignmentFollowedBySequenceIsNoRecordLiteral() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Host {
                var point: Point
                var other: Point
                var number: int := 0
                redefine tran { point := other { number := 1 } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var steps = parsedPackage
                .classByName("Host")
                .anonymousMain()
                .branches()
                .getFirst()
                .getSteps();
        assertThat(steps).hasSize(2);
        assertThat(((AssignmentOperation) steps.get(0)).getExpression()).isInstanceOf(ElementReference.class);
        assertThat(steps.get(1)).isInstanceOf(SequenceOperation.class);
    }

    @Test
    void recordNameWithEmptyBracesIsNoRecordLiteral() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Host {
                var point: Point
                redefine tran { point := Point {} }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var main = parsedPackage.classByName("Host").anonymousMain().eObject();
        assertThat(EcoreUtil2.eAllOfType(main, RecordLiteral.class)).isEmpty();
        assertThat(EcoreUtil2.eAllOfType(main, FieldAssignment.class)).isEmpty();
    }
}
