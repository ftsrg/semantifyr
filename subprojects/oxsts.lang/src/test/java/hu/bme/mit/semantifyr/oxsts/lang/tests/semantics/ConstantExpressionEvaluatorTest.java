/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.semantics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.BooleanEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@InjectWithOxsts
public class ConstantExpressionEvaluatorTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private ConstantExpressionEvaluatorProvider constantExpressionEvaluatorProvider;

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
    void rangeExclusiveFromZeroToZeroIsEmpty() {
        var range = evaluateFirstRange("0..<0");
        assertThat(range.getLowerBound()).isEqualTo(0);
        assertThat(range.getUpperBound()).isEqualTo(-1);
        assertThat(range.isBounded()).isTrue();
    }

    @Test
    void rangeFromMinusOneKeepsItsLowerBound() {
        var range = evaluateFirstRange("-1..1");
        assertThat(range.getLowerBound()).isEqualTo(-1);
        assertThat(range.getUpperBound()).isEqualTo(1);
        assertThat(range.isBounded()).isTrue();
    }

    @Test
    void rangeEndingAtMinusOneKeepsItsUpperBound() {
        var range = evaluateFirstRange("-3..-1");
        assertThat(range.getLowerBound()).isEqualTo(-3);
        assertThat(range.getUpperBound()).isEqualTo(-1);
        assertThat(range.isBounded()).isTrue();
    }

    @Test
    void rangeExclusiveBelowTheLeastIntIsEmpty() {
        var range = evaluateFirstRange("0..<-2147483647 - 1");
        assertThat(range.getUpperBound()).isEqualTo((long) Integer.MIN_VALUE - 1);
        assertThat(range.isBounded()).isTrue();
    }

    @Test
    void rangeExclusiveWithInfinityStaysUnbounded() {
        var range = evaluateFirstRange("1..<*");
        assertThat(range.getLowerBound()).isEqualTo(1);
        assertThat(range.getUpperBound()).isEqualTo(RangeEvaluation.INFINITY);
        assertThat(range.isBounded()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            value = {
                "2147483647 + 1",
                "-2147483647 - 2",
                "65536 * 65536",
                "-(-2147483647 - 1)",
                "(-2147483647 - 1) / -1",
            })
    void integerArithmeticBeyondTheEvaluatedIntegersHasNoConstantValue(String expressionText) {
        var initializer = parseTotalInitializer(expressionText);
        assertThatThrownBy(() -> constantExpressionEvaluatorProvider.evaluate(initializer))
                .isInstanceOf(EvaluationFailureException.class)
                .hasMessageContaining("outside the integers");
    }

    @Test
    void integerArithmeticReachingTheLeastIntEvaluates() {
        var initializer = parseTotalInitializer("-2147483647 - 1");
        assertThat(constantExpressionEvaluatorProvider.evaluate(initializer))
                .isEqualTo(new IntegerEvaluation(Integer.MIN_VALUE));
    }

    @Test
    void integerDivisionOfNonNegativeOperandsRoundsDown() {
        assertThat(constantExpressionEvaluatorProvider.evaluate(parseTotalInitializer("7 / 2")))
                .isEqualTo(new IntegerEvaluation(3));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            value = {
                "true && false; false",
                "true && true; true",
                "false || true; true",
                "false || false; false",
                "true ^^ true; false",
                "true ^^ false; true",
                "!true; false",
                "true == false; false",
                "true != false; true",
            })
    void booleanOperatorsOnConstantsEvaluate(String expressionText, boolean expected) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var flag: bool := %s
            }
            """.formatted(expressionText));
        parsedPackage.assertNoResourceErrors();
        var initializer =
                parsedPackage.classByName("Host").variableByName("flag").initializer();
        assertThat(constantExpressionEvaluatorProvider.evaluate(initializer))
                .isEqualTo(new BooleanEvaluation(expected));
    }

    private Expression parseTotalInitializer(String expressionText) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var total: int := %s
            }
            """.formatted(expressionText));
        parsedPackage.assertNoResourceErrors();
        return parsedPackage.classByName("Host").variableByName("total").initializer();
    }

    private RangeEvaluation evaluateFirstRange(String rangeText) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var total: int := 0
                redefine tran {
                    inline for (index in %s) {
                        total := total + 1
                    }
                }
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
}
