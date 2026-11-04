/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.typesystem;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ParameterBindingTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void allRequiredArgumentsBound_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran addBy(amount: int) { count := count + amount }
                redefine tran { inline addBy(5) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void missingRequiredArgument_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran addBy(amount: int) { count := count + amount }
                redefine tran { inline addBy() }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "non-optional");
    }

    @Test
    void missingSomeRequiredArguments_listsTheUnboundNames() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran addTwo(first: int, second: int) { count := count + first + second }
                redefine tran { inline addTwo(1) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "second");
    }

    @Test
    void extraArgument_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran addBy(amount: int) { count := count + amount }
                redefine tran { inline addBy(1, 2) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "at most");
    }

    @Test
    void optionalParameterMayBeOmitted() {
        // Parameter `delta` is optional (lower bound 0). Omitting it is
        // legal. The tran body refers to `delta` only conditionally, so
        // the model compiles even when no binding is provided (the inliner
        // handles the omitted-parameter case as absent).
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran addOptional(amount: int, delta: int[0..1]) { count := count + amount }
                redefine tran { inline addOptional(5) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void optionalParameterMayAlsoBeBound() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran addOptional(amount: int, delta: int[0..1]) { count := count + amount }
                redefine tran { inline addOptional(5, 2) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void missingRequiredBeforeOptional_isRejected() {
        // Required amount is missing; optional delta is present via named arg.
        // Using positional-only for simplicity here: no arguments at all.
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran addOptional(amount: int, delta: int[0..1]) { count := count + amount }
                redefine tran { inline addOptional() }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "amount");
    }

    @Test
    void requiredBeforeOptional_isAccepted() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran addOptional(amount: int, delta: int[0..1]) { count := count + amount }
                redefine tran { inline addOptional(5) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void requiredAfterOptional_isRejected() {
        // `delta` is optional [0..1] but precedes the required `amount`.
        // Positional calls would be ambiguous if `delta` is omitted, so
        // the declaration itself is rejected.
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran addBad(delta: int[0..1], amount: int) { count := count + amount }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(
                OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "cannot follow optional parameter");
    }

    @Test
    void multipleOptionalsAfterRequired_isAccepted() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran threeParameters(required: int, firstOptional: int[0..1], secondOptional: int[0..1]) { count := count + required }
                redefine tran { inline threeParameters(1) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void requiredBetweenOptionals_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran messy(first: int, firstOptional: int[0..1], second: int, secondOptional: int[0..1]) { count := count + first + second }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "'second'");
    }

    @Test
    void annotationDeclarationParameterOrder_isAlsoChecked() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Tagged(maybe: string[0..1], required: string)
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(
                OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "cannot follow optional parameter");
    }

    @Test
    void annotationPositionalArgumentAfterNamed_bindsTheParameterAtItsPosition() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Pair(first: int, second: bool)
            @Pair(first = 1, true)
            class Annotated { }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void annotationNamedArgument_bindsTheParameterOfThatName() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Pair(first: int, second: int[0..1])
            @Pair(second = 2)
            class Annotated { }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
        var annotation = parsedPackage
                .classByName("Annotated")
                .eObject()
                .getAnnotation()
                .getAnnotations()
                .getFirst();
        var parameters = annotation.getDeclaration().getParameters();
        assertThat(OxstsUtils.getAnnotationValue(annotation, parameters.get(0))).isNull();
        assertThat(OxstsUtils.getAnnotationValue(annotation, parameters.get(1)))
                .isInstanceOfSatisfying(
                        LiteralInteger.class,
                        value -> assertThat(value.getValue()).isEqualTo(2));
    }

    @Test
    void annotationNamedArgumentOfWrongType_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Pair(first: int, second: bool)
            @Pair(second = 1, first = 2)
            class Annotated { }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "parameter 'second'");
    }
}
