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
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.WrappedOxstsPackage;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ArrayTypeTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void indexingWithIntConstantPasses() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[3] := [0, 0, 0]
                redefine tran {
                    values[0] := 1
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void indexingWithIntVariablePasses() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[3] := [0, 0, 0]
                var i: int := 0
                redefine tran {
                    values[i] := 1
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void wholeArrayOperationsPass() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[2] := [0, 0]
                var backup: int[2] := [0, 0]
                var same: bool := false
                redefine tran {
                    backup := values
                    values := [1, 2]
                    havoc(values)
                    same := values == backup || values != [3, 4]
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void comparingArraysOfDifferentSizesIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var pair: int[2] := [0, 0]
                var triple: int[3] := [0, 0, 0]
                prop { return AG pair == triple }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "array of the same size");
    }

    @Test
    void comparingAnArrayWithAScalarIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var pair: int[2] := [0, 0]
                prop { return AG pair != 1 }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "array of the same size");
    }

    @Test
    void orderingArraysIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var pair: int[2] := [0, 0]
                var other: int[2] := [0, 0]
                prop { return AG pair < other }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "array of the same size");
    }

    @Test
    void indexingWithBoolIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[3] := [0, 0, 0]
                var b: bool := false
                redefine tran {
                    values[b] := 1
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "numeric");
    }

    @Test
    void arrayLiteralWithHomogeneousElementsPasses() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[3] := [1, 2, 3]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void arrayLiteralWithMixedTypesIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[2] := [1, true]
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "common type");
    }

    @Test
    void variableWithUnboundedArrayIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var buffer: int[*] := [0]
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "compile-time upper bound");
    }

    @Test
    void classTypedArrayIsRejectedAtItsTypeSpecification() {
        var pkg = parseHelper.parse("""
            package test
            class Worker { }
            class C {
                contains worker: Worker
                var slots: Worker[2]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "element type 'Worker'");
        assertOnlyErrorsAreUnsupportedAtLine(pkg, 5);
    }

    @Test
    void featureTypedArrayIsRejectedAtItsTypeSpecification() {
        var pkg = parseHelper.parse("""
            package test
            class Worker { }
            class C {
                contains workers: Worker[0..*]
                contains first: Worker subsets workers
                var slots: workers[1..2]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "element type 'workers'");
        assertOnlyErrorsAreUnsupportedAtLine(pkg, 6);
    }

    @Test
    void optionalClassTypedVariableIsNotAnArray() {
        var pkg = parseHelper.parse("""
            package test
            class Worker { }
            class C {
                contains worker: Worker
                var current: Worker[0..1] := nothing
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void enumTypedArrayPasses() {
        var pkg = parseHelper.parse("""
            package test
            enum Color { Red, Green }
            class C {
                var colors: Color[2] := [Color::Red, Color::Green]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    private static void assertOnlyErrorsAreUnsupportedAtLine(WrappedOxstsPackage pkg, int line) {
        assertThat(pkg.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.UNSUPPORTED_FEATURE);
                    assertThat(issue.getLineNumber()).isEqualTo(line);
                });
    }

    @Test
    void indexedExpressionTypeIsTheElementType() {
        // Assignment of `values[0]` (int element) to an int variable works.
        // If we didn't reduce the array's multiplicity to ONE on indexing,
        // this would flag a multiplicity mismatch.
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[3] := [1, 2, 3]
                var first: int := 0
                redefine tran {
                    first := values[0]
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void indexingIntoScalarIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var scalar: int := 0
                redefine tran {
                    scalar[0] := 1
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "non-array");
    }
}
