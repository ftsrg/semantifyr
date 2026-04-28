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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class OperatorSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void arithmeticAdd() {
        assertArithmeticBinaryOp("x + 1", ArithmeticOp.ADD);
    }

    @Test
    void arithmeticSub() {
        assertArithmeticBinaryOp("x - 1", ArithmeticOp.SUB);
    }

    @Test
    void arithmeticMul() {
        assertArithmeticBinaryOp("x * 2", ArithmeticOp.MUL);
    }

    @Test
    void arithmeticDiv() {
        assertArithmeticBinaryOp("x / 2", ArithmeticOp.DIV);
    }

    private void assertArithmeticBinaryOp(String expression, ArithmeticOp expectedOp) {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := %s }
            }
            """.formatted(expression));
        pkg.assertNoResourceErrors();
        var ops = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), ArithmeticBinaryOperator.class);
        assertThat(ops).isNotEmpty();
        assertThat(ops.getFirst().getOp()).isEqualTo(expectedOp);
    }

    @Test
    void arithmeticUnaryPlus() {
        assertArithmeticUnaryOp("+x", UnaryOp.PLUS);
    }

    @Test
    void arithmeticUnaryMinus() {
        assertArithmeticUnaryOp("-x", UnaryOp.MINUS);
    }

    private void assertArithmeticUnaryOp(String expression, UnaryOp expectedOp) {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                var y: int := 0
                redefine tran { y := %s }
            }
            """.formatted(expression));
        pkg.assertNoResourceErrors();
        var ops = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), ArithmeticUnaryOperator.class);
        assertThat(ops).isNotEmpty();
        assertThat(ops.getFirst().getOp()).isEqualTo(expectedOp);
    }

    @Test
    void booleanAnd() {
        assertBooleanOp("a && b", BooleanOp.AND);
    }

    @Test
    void booleanOr() {
        assertBooleanOp("a || b", BooleanOp.OR);
    }

    @Test
    void booleanXor() {
        assertBooleanOp("a ^^ b", BooleanOp.XOR);
    }

    private void assertBooleanOp(String expression, BooleanOp expectedOp) {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var a: bool := false
                var b: bool := false
                redefine tran { assume(%s) }
            }
            """.formatted(expression));
        pkg.assertNoResourceErrors();
        var ops = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), BooleanOperator.class);
        assertThat(ops).isNotEmpty();
        assertThat(ops.getFirst().getOp()).isEqualTo(expectedOp);
    }

    @Test
    void comparisonLess() {
        assertComparisonOp("x < 5", ComparisonOp.LESS);
    }

    @Test
    void comparisonLessEq() {
        assertComparisonOp("x <= 5", ComparisonOp.LESS_EQ);
    }

    @Test
    void comparisonGreater() {
        assertComparisonOp("x > 5", ComparisonOp.GREATER);
    }

    @Test
    void comparisonGreaterEq() {
        assertComparisonOp("x >= 5", ComparisonOp.GREATER_EQ);
    }

    @Test
    void comparisonEq() {
        assertComparisonOp("x == 5", ComparisonOp.EQ);
    }

    @Test
    void comparisonNotEq() {
        assertComparisonOp("x != 5", ComparisonOp.NOT_EQ);
    }

    private void assertComparisonOp(String expression, ComparisonOp expectedOp) {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { assume(%s) }
            }
            """.formatted(expression));
        pkg.assertNoResourceErrors();
        var ops = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), ComparisonOperator.class);
        assertThat(ops).isNotEmpty();
        assertThat(ops.getFirst().getOp()).isEqualTo(expectedOp);
    }

    @Test
    void negationOfBoolean() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                redefine tran { assume(!b) }
            }
            """);
        pkg.assertNoResourceErrors();
        var ops = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), NegationOperator.class);
        assertThat(ops).isNotEmpty();
        assertThat(ops.getFirst().getBody()).isNotNull();
    }

    @Test
    void rangeInclusive() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                redefine tran { inline for (n in 1..5) { total := total + 1 } }
            }
            """);
        pkg.assertNoResourceErrors();
        var ranges = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), RangeExpression.class);
        assertThat(ranges).hasSize(1);
        assertThat(ranges.getFirst().isExclusive()).isFalse();
    }

    @Test
    void rangeExclusive() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                redefine tran { inline for (n in 1..<5) { total := total + 1 } }
            }
            """);
        pkg.assertNoResourceErrors();
        var ranges = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), RangeExpression.class);
        assertThat(ranges).hasSize(1);
        assertThat(ranges.getFirst().isExclusive()).isTrue();
    }

    @Test
    void temporalAG() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := 1 }
                prop { return AG (x >= 0) }
            }
            """);
        pkg.assertNoResourceErrors();
        var ag = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousProperty().eObject(), AG.class);
        assertThat(ag).hasSize(1);
        assertThat(ag.getFirst().getBody()).isNotNull();
    }

    @Test
    void temporalEF() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := x + 1 }
                prop { return EF (x == 5) }
            }
            """);
        pkg.assertNoResourceErrors();
        var ef = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousProperty().eObject(), EF.class);
        assertThat(ef).hasSize(1);
        assertThat(ef.getFirst().getBody()).isNotNull();
    }

    @Test
    void ifThenElseExpression() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var n: int := 0
                redefine tran {
                    var result: int := 0
                    result := if n > 0 then 1 else -1
                }
            }
            """);
        pkg.assertNoResourceErrors();
        var ite = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), IfThenElse.class);
        assertThat(ite).hasSize(1);
        var first = ite.getFirst();
        assertThat(first.getGuard()).isNotNull();
        assertThat(first.getThen()).isNotNull();
        assertThat(first.getElse()).isNotNull();
    }

    @Test
    void parenthesisedExpressionPreservesStructure() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := (x + 1) * 2 }
            }
            """);
        pkg.assertNoResourceErrors();
        var ops = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), ArithmeticBinaryOperator.class);
        assertThat(ops).hasSize(2);
    }
}
