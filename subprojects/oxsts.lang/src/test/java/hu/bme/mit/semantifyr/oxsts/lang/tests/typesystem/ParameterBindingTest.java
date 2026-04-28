/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.typesystem;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ParameterBindingTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void allRequiredArgumentsBound_passes() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addBy(n: int) { x := x + n }
                redefine tran { inline addBy(5) }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void missingRequiredArgument_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addBy(n: int) { x := x + n }
                redefine tran { inline addBy() }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "non-optional");
    }

    @Test
    void missingSomeRequiredArguments_listsTheUnboundNames() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addTwo(a: int, b: int) { x := x + a + b }
                redefine tran { inline addTwo(1) }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "b");
    }

    @Test
    void extraArgument_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addBy(n: int) { x := x + n }
                redefine tran { inline addBy(1, 2) }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "at most");
    }

    @Test
    void optionalParameterMayBeOmitted() {
        // Parameter `delta` is optional (lower bound 0). Omitting it is
        // legal. The tran body refers to `delta` only conditionally, so
        // the model compiles even when no binding is provided (the inliner
        // handles the omitted-parameter case as absent).
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addOptional(n: int, delta: int[0..1]) { x := x + n }
                redefine tran { inline addOptional(5) }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void optionalParameterMayAlsoBeBound() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addOptional(n: int, delta: int[0..1]) { x := x + n }
                redefine tran { inline addOptional(5, 2) }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void missingRequiredBeforeOptional_isRejected() {
        // Required n is missing; optional delta is present via named arg.
        // Using positional-only for simplicity here: no arguments at all.
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addOptional(n: int, delta: int[0..1]) { x := x + n }
                redefine tran { inline addOptional() }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "n");
    }

    @Test
    void requiredBeforeOptional_isAccepted() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addOptional(n: int, delta: int[0..1]) { x := x + n }
                redefine tran { inline addOptional(5) }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void requiredAfterOptional_isRejected() {
        // `delta` is optional [0..1] but precedes the required `n`.
        // Positional calls would be ambiguous if `delta` is omitted, so
        // the declaration itself is rejected.
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addBad(delta: int[0..1], n: int) { x := x + n }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "cannot follow optional parameter");
    }

    @Test
    void multipleOptionalsAfterRequired_isAccepted() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran threeArg(a: int, b: int[0..1], c: int[0..1]) { x := x + a }
                redefine tran { inline threeArg(1) }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void requiredBetweenOptionals_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran messy(a: int, b: int[0..1], c: int, d: int[0..1]) { x := x + a + c }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "'c'");
    }

    @Test
    void annotationDeclarationParameterOrder_isAlsoChecked() {
        var pkg = parseHelper.parse("""
            package test
            annotation Tagged(maybe: string[0..1], required: string)
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "cannot follow optional parameter");
    }
}
