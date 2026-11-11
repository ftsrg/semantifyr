/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.WrappedOxstsPackage;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class DuplicateDeclarationValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void duplicateTransitionParametersAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var count: int := 0
                tran add(amount: int, amount: int) {
                    count := count + amount
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertErrors(
                parsedPackage, OxstsValidator.DUPLICATE_NAME_ISSUE + "@4", OxstsValidator.DUPLICATE_NAME_ISSUE + "@4");
        parsedPackage.assertHasValidationIssue(OxstsValidator.DUPLICATE_NAME_ISSUE, "'amount'");
    }

    @Test
    void duplicatePropertyParametersAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                prop larger(
                    limit: int,
                    limit: int,
                ): bool {
                    return limit > 0
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertErrors(
                parsedPackage, OxstsValidator.DUPLICATE_NAME_ISSUE + "@4", OxstsValidator.DUPLICATE_NAME_ISSUE + "@5");
    }

    @Test
    void distinctParametersPass() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var count: int := 0
                tran add(amount: int, factor: int) {
                    count := count + amount * factor
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void duplicateLocalVariablesOfOneSequenceAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var count: int := 0
                redefine tran {
                    var step: int := 1
                    var step: int := 2
                    count := count + step
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertErrors(
                parsedPackage, OxstsValidator.DUPLICATE_NAME_ISSUE + "@5", OxstsValidator.DUPLICATE_NAME_ISSUE + "@6");
    }

    @Test
    void localVariableOfANestedSequenceMayReuseAnOuterName() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var count: int := 0
                redefine tran {
                    var step: int := 1
                    choice {
                        var step: int := 2
                        count := count + step
                    } or {
                        count := count + step
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void twoFeaturesRedefiningOneInheritedFeatureAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Base {
                contains part: Item
            }
            class Child : Base {
                contains first: Item redefines part
                contains second: Item redefines part
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertErrors(
                parsedPackage,
                OxstsValidator.DUPLICATE_REDEFINITION + "@7",
                OxstsValidator.DUPLICATE_REDEFINITION + "@8");
        parsedPackage.assertHasValidationIssue(OxstsValidator.DUPLICATE_REDEFINITION, "'first' and 'second'");
    }

    @Test
    void redefineAndRedefinesOfOneInheritedFeatureAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Base {
                contains part: Item
            }
            class Child : Base {
                redefine contains part: Item
                contains other: Item redefines part
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertErrors(
                parsedPackage,
                OxstsValidator.DUPLICATE_REDEFINITION + "@7",
                OxstsValidator.DUPLICATE_REDEFINITION + "@8");
    }

    @Test
    void twoInnerFeaturesRedefiningOneFeatureAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Inner {
                refers target: Item[0..1]
            }
            class Outer {
                contains first: Item
                contains inner: Inner {
                    redefine refers target: Item[0..1] = first
                    refers other: Item[0..1] redefines target
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertErrors(
                parsedPackage,
                OxstsValidator.DUPLICATE_REDEFINITION + "@9",
                OxstsValidator.DUPLICATE_REDEFINITION + "@10");
    }

    @Test
    void oneRedefinitionPerInheritedFeaturePasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Base {
                contains first: Item
                contains second: Item
            }
            class Child : Base {
                redefine contains first: Item
                contains renamed: Item redefines second
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    private static void assertErrors(WrappedOxstsPackage parsedPackage, String... codesAtLines) {
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> {
                    return issue.getSeverity() == Severity.ERROR;
                })
                .extracting(issue -> {
                    return issue.getCode() + "@" + issue.getLineNumber();
                })
                .containsExactlyInAnyOrder(codesAtLines);
    }
}
