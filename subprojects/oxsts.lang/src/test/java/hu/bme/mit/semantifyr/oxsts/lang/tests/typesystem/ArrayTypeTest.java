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
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var values: int[3] := [0, 0, 0]
                redefine tran {
                    values[0] := 1
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void indexingWithIntVariablePasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var values: int[3] := [0, 0, 0]
                var index: int := 0
                redefine tran {
                    values[index] := 1
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void wholeArrayOperationsPass() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
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

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void comparingArraysOfDifferentSizesIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var pair: int[2] := [0, 0]
                var triple: int[3] := [0, 0, 0]
                prop { return AG pair == triple }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "array of the same size");
    }

    @Test
    void comparingAnArrayWithAScalarIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var pair: int[2] := [0, 0]
                prop { return AG pair != 1 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "array of the same size");
    }

    @Test
    void orderingArraysIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var pair: int[2] := [0, 0]
                var other: int[2] := [0, 0]
                prop { return AG pair < other }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "array of the same size");
    }

    @Test
    void indexingWithBoolIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var values: int[3] := [0, 0, 0]
                var flag: bool := false
                redefine tran {
                    values[flag] := 1
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "numeric");
    }

    @Test
    void arrayLiteralWithHomogeneousElementsPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var values: int[3] := [1, 2, 3]
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void arrayLiteralWithMixedTypesIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var values: int[2] := [1, true]
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "common type");
    }

    @Test
    void variableWithUnboundedArrayIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var buffer: int[*] := [0]
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "compile-time upper bound");
    }

    @Test
    void classAndFeatureTypedArraysPass() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker {
                var done: bool := false
                tran step() { done := true }
                prop isDone(): bool { return done }
            }
            class Team {
                contains workers: Worker[0..*]
                contains first: Worker subsets workers
                contains second: Worker subsets workers
                var queue: Worker[2] := [first, second]
                var picks: workers[1..2] := [first, second]
                var index: int := 0
                redefine tran {
                    queue[index] := second
                    picks := queue
                    havoc(picks)
                    inline queue[index].step()
                }
                prop { return EF (picks[0] == first && queue[1].isDone()) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void memberAccessThroughAnArraySlotIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker {
                var done: bool := false
            }
            class Team {
                contains first: Worker
                var queue: Worker[2] := [first, first]
                prop { return EF queue[0].done }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "through variable 'queue'");
        assertOnlyErrorsAreUnsupportedAtLine(parsedPackage, 8);
    }

    @Test
    void localClassTypedArrayIsAccepted() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker { }
            class Team {
                contains worker: Worker
                redefine tran {
                    var slots: Worker[2] := [worker, worker]
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void optionalClassTypedVariableIsNotAnArray() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker { }
            class Team {
                contains worker: Worker
                var current: Worker[0..1] := nothing
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void enumTypedArrayPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            enum Color { Red, Green }
            class Buffer {
                var colors: Color[2] := [Color::Red, Color::Green]
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    private static void assertOnlyErrorsAreUnsupportedAtLine(WrappedOxstsPackage parsedPackage, int line) {
        assertThat(parsedPackage.getIssues())
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
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var values: int[3] := [1, 2, 3]
                var first: int := 0
                redefine tran {
                    first := values[0]
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void indexingIntoScalarIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var scalar: int := 0
                redefine tran {
                    scalar[0] := 1
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "non-array");
    }
}
