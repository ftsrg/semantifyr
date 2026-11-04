/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.syntax;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@InjectWithOxsts
public class OperatorSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private ConstantExpressionEvaluatorProvider constantExpressionEvaluatorProvider;

    @Test
    void arithmeticAdd() {
        assertArithmeticBinaryOperator("x + 1", ArithmeticOp.ADD);
    }

    @Test
    void arithmeticSubtract() {
        assertArithmeticBinaryOperator("x - 1", ArithmeticOp.SUB);
    }

    @Test
    void arithmeticMultiply() {
        assertArithmeticBinaryOperator("x * 2", ArithmeticOp.MUL);
    }

    @Test
    void arithmeticDivide() {
        assertArithmeticBinaryOperator("x / 2", ArithmeticOp.DIV);
    }

    private void assertArithmeticBinaryOperator(String expression, ArithmeticOp expectedOperator) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var x: int := 0
                redefine tran { x := %s }
            }
            """.formatted(expression));
        parsedPackage.assertNoResourceErrors();
        var operators = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), ArithmeticBinaryOperator.class);
        assertThat(operators).isNotEmpty();
        assertThat(operators.getFirst().getOp()).isEqualTo(expectedOperator);
    }

    @Test
    void arithmeticUnaryPlus() {
        assertArithmeticUnaryOperator("+x", UnaryOp.PLUS);
    }

    @Test
    void arithmeticUnaryMinus() {
        assertArithmeticUnaryOperator("-x", UnaryOp.MINUS);
    }

    private void assertArithmeticUnaryOperator(String expression, UnaryOp expectedOperator) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var x: int := 0
                var y: int := 0
                redefine tran { y := %s }
            }
            """.formatted(expression));
        parsedPackage.assertNoResourceErrors();
        var operators = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), ArithmeticUnaryOperator.class);
        assertThat(operators).isNotEmpty();
        assertThat(operators.getFirst().getOp()).isEqualTo(expectedOperator);
    }

    @ParameterizedTest
    @CsvSource({
        "x + y * z, (x + (y * z))",
        "x * y + z, ((x * y) + z)",
        "x - y / z, (x - (y / z))",
        "x - y - z, ((x - y) - z)",
        "x / y * z, ((x / y) * z)",
        "(x + y) * z, ((x + y) * z)",
        "-x * y, ((-x) * y)",
        "x..y + z, (x..(y + z))",
        "x + y < z, ((x + y) < z)",
        "x < y && y <= z, ((x < y) && (y <= z))",
        "x == y || !(y != z), ((x == y) || (!(y != z)))",
    })
    void operatorsBindByTheirPrecedenceLevel(String expression, String expectedReading) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var x: int := 0
                var y: int := 0
                var z: int := 0
                redefine tran { assume(%s) }
            }
            """.formatted(expression));
        parsedPackage.assertNoResourceErrors();
        var assumption = EcoreUtil2.eAllOfType(
                        parsedPackage.classByName("Host").anonymousMain().eObject(), AssumptionOperation.class)
                .getFirst();
        assertThat(parenthesize(assumption.getExpression())).isEqualTo(expectedReading);
    }

    @Test
    void booleanAnd() {
        assertBooleanOperator("a && b", BooleanOp.AND);
    }

    @Test
    void booleanOr() {
        assertBooleanOperator("a || b", BooleanOp.OR);
    }

    @Test
    void booleanXor() {
        assertBooleanOperator("a ^^ b", BooleanOp.XOR);
    }

    private void assertBooleanOperator(String expression, BooleanOp expectedOperator) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var a: bool := false
                var b: bool := false
                redefine tran { assume(%s) }
            }
            """.formatted(expression));
        parsedPackage.assertNoResourceErrors();
        var operators = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), BooleanOperator.class);
        assertThat(operators).isNotEmpty();
        assertThat(operators.getFirst().getOp()).isEqualTo(expectedOperator);
    }

    @ParameterizedTest
    @CsvSource({
        "a || b && c, (a || (b && c))",
        "a && b || c, ((a && b) || c)",
        "a ^^ b && c, (a ^^ (b && c))",
        "a || b ^^ c, (a || (b ^^ c))",
        "a ^^ b || c, ((a ^^ b) || c)",
        "a || b || c, ((a || b) || c)",
        "a && b && c, ((a && b) && c)",
        "(a || b) && c, ((a || b) && c)",
    })
    void booleanOperatorsBindAndThenXorThenOr(String expression, String expectedReading) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var a: bool := false
                var b: bool := false
                var c: bool := false
                redefine tran { assume(%s) }
            }
            """.formatted(expression));
        parsedPackage.assertNoResourceErrors();
        var assumption = EcoreUtil2.eAllOfType(
                        parsedPackage.classByName("Host").anonymousMain().eObject(), AssumptionOperation.class)
                .getFirst();
        assertThat(parenthesize(assumption.getExpression())).isEqualTo(expectedReading);
    }

    private static String parenthesize(Expression expression) {
        if (expression instanceof BooleanOperator operator) {
            var symbol = switch (operator.getOp()) {
                case AND -> "&&";
                case OR -> "||";
                case XOR -> "^^";
            };
            return "(" + parenthesize(operator.getLeft()) + " " + symbol + " " + parenthesize(operator.getRight())
                    + ")";
        }
        if (expression instanceof ComparisonOperator operator) {
            var symbol = switch (operator.getOp()) {
                case LESS -> "<";
                case LESS_EQ -> "<=";
                case GREATER -> ">";
                case GREATER_EQ -> ">=";
                case EQ -> "==";
                case NOT_EQ -> "!=";
            };
            return "(" + parenthesize(operator.getLeft()) + " " + symbol + " " + parenthesize(operator.getRight())
                    + ")";
        }
        if (expression instanceof ArithmeticBinaryOperator operator) {
            var symbol = switch (operator.getOp()) {
                case ADD -> "+";
                case SUB -> "-";
                case MUL -> "*";
                case DIV -> "/";
            };
            return "(" + parenthesize(operator.getLeft()) + " " + symbol + " " + parenthesize(operator.getRight())
                    + ")";
        }
        if (expression instanceof RangeExpression range) {
            return "(" + parenthesize(range.getLeft()) + (range.isExclusive() ? "..<" : "..")
                    + parenthesize(range.getRight()) + ")";
        }
        if (expression instanceof ArithmeticUnaryOperator operator) {
            var symbol = switch (operator.getOp()) {
                case PLUS -> "+";
                case MINUS -> "-";
            };
            return "(" + symbol + parenthesize(operator.getBody()) + ")";
        }
        if (expression instanceof NegationOperator operator) {
            return "(!" + parenthesize(operator.getBody()) + ")";
        }
        return NamingUtil.getName(((ElementReference) expression).getElement());
    }

    @Test
    void comparisonLess() {
        assertComparisonOperator("x < 5", ComparisonOp.LESS);
    }

    @Test
    void comparisonLessOrEqual() {
        assertComparisonOperator("x <= 5", ComparisonOp.LESS_EQ);
    }

    @Test
    void comparisonGreater() {
        assertComparisonOperator("x > 5", ComparisonOp.GREATER);
    }

    @Test
    void comparisonGreaterOrEqual() {
        assertComparisonOperator("x >= 5", ComparisonOp.GREATER_EQ);
    }

    @Test
    void comparisonEqual() {
        assertComparisonOperator("x == 5", ComparisonOp.EQ);
    }

    @Test
    void comparisonNotEqual() {
        assertComparisonOperator("x != 5", ComparisonOp.NOT_EQ);
    }

    private void assertComparisonOperator(String expression, ComparisonOp expectedOperator) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var x: int := 0
                redefine tran { assume(%s) }
            }
            """.formatted(expression));
        parsedPackage.assertNoResourceErrors();
        var operators = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), ComparisonOperator.class);
        assertThat(operators).isNotEmpty();
        assertThat(operators.getFirst().getOp()).isEqualTo(expectedOperator);
    }

    @Test
    void negationOfBoolean() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var flag: bool := false
                redefine tran { assume(!flag) }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var operators = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), NegationOperator.class);
        assertThat(operators).isNotEmpty();
        assertThat(operators.getFirst().getBody()).isNotNull();
    }

    @Test
    void rangeInclusive() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var total: int := 0
                redefine tran { inline for (index in 1..5) { total := total + 1 } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var ranges = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), RangeExpression.class);
        assertThat(ranges).hasSize(1);
        assertThat(ranges.getFirst().isExclusive()).isFalse();
    }

    @Test
    void rangeExclusive() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var total: int := 0
                redefine tran { inline for (index in 1..<5) { total := total + 1 } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var ranges = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), RangeExpression.class);
        assertThat(ranges).hasSize(1);
        assertThat(ranges.getFirst().isExclusive()).isTrue();
    }

    @Test
    void rangeInclusiveEvaluatesToBothBounds() {
        var range = evaluateFirstRange("1..5");
        assertThat(range.getLowerBound()).isEqualTo(1);
        assertThat(range.getUpperBound()).isEqualTo(5);
    }

    @Test
    void rangeExclusiveEvaluatesWithoutUpperBound() {
        var range = evaluateFirstRange("1..<5");
        assertThat(range.getLowerBound()).isEqualTo(1);
        assertThat(range.getUpperBound()).isEqualTo(4);
    }

    @Test
    void rangeExclusiveWithEqualBoundsIsEmpty() {
        var range = evaluateFirstRange("2..<2");
        assertThat(range.getLowerBound()).isEqualTo(2);
        assertThat(range.getUpperBound()).isEqualTo(1);
    }

    @Test
    void rangeExclusiveWithInfinityStaysUnbounded() {
        var range = evaluateFirstRange("1..<*");
        assertThat(range.getLowerBound()).isEqualTo(1);
        assertThat(range.getUpperBound()).isEqualTo(RangeEvaluation.INFINITY);
    }

    private RangeEvaluation evaluateFirstRange(String rangeText) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var total: int := 0
                redefine tran { inline for (index in %s) { total := total + 1 } }
            }
            """.formatted(rangeText));
        parsedPackage.assertNoResourceErrors();
        var ranges = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), RangeExpression.class);
        assertThat(ranges).hasSize(1);
        var evaluation = constantExpressionEvaluatorProvider.evaluate(ranges.getFirst());
        assertThat(evaluation).isInstanceOf(RangeEvaluation.class);
        return (RangeEvaluation) evaluation;
    }

    @Test
    void temporalAG() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var x: int := 0
                redefine tran { x := 1 }
                prop { return AG (x >= 0) }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var alwaysGlobally = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousProperty().eObject(), AG.class);
        assertThat(alwaysGlobally).hasSize(1);
        assertThat(alwaysGlobally.getFirst().getBody()).isNotNull();
    }

    @Test
    void temporalEF() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var x: int := 0
                redefine tran { x := x + 1 }
                prop { return EF (x == 5) }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var existsFinally = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousProperty().eObject(), EF.class);
        assertThat(existsFinally).hasSize(1);
        assertThat(existsFinally.getFirst().getBody()).isNotNull();
    }

    @Test
    void ifThenElseExpression() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var number: int := 0
                redefine tran {
                    var result: int := 0
                    result := if number > 0 then 1 else -1
                }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var ifThenElses = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), IfThenElse.class);
        assertThat(ifThenElses).hasSize(1);
        var first = ifThenElses.getFirst();
        assertThat(first.getGuard()).isNotNull();
        assertThat(first.getThen()).isNotNull();
        assertThat(first.getElse()).isNotNull();
    }

    @Test
    void parenthesisedExpressionPreservesStructure() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var x: int := 0
                redefine tran { x := (x + 1) * 2 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var operators = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), ArithmeticBinaryOperator.class);
        assertThat(operators).hasSize(2);
    }
}
