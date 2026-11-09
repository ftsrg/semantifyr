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
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import java.util.List;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class VariableInitializerValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private ParseHelper<InlinedOxsts> inlinedParseHelper;

    @Inject
    private IResourceValidator resourceValidator;

    @Test
    void classVariableWithoutInitializerIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyMissingInitializers(parsedPackage.getIssues(), "'count'");
    }

    @Test
    void localVariableWithoutInitializerIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                redefine tran {
                    var next: int
                    count := next
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyMissingInitializers(parsedPackage.getIssues(), "'next'");
    }

    @Test
    void recordFieldWithoutInitializerIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            record Point {
                var x: int
                var y: int := 0
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyMissingInitializers(parsedPackage.getIssues(), "'x'");
    }

    @Test
    void recordVariableWhoseFieldsAllHaveDefaultsStillNeedsAnInitializer() {
        var parsedPackage = parseHelper.parse("""
            package test
            record Point {
                var x: int := 0
                var y: int := 0
            }
            class Holder {
                var position: Point
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyMissingInitializers(parsedPackage.getIssues(), "'position'");
    }

    @Test
    void classTypedVariableWithoutInitializerIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker {
            }
            class Pool {
                contains first: Worker
                var picked: Worker
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyMissingInitializers(parsedPackage.getIssues(), "'picked'");
    }

    @Test
    void clockWithoutInitializerIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Timer {
                @Clock
                var elapsed: int
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyMissingInitializers(parsedPackage.getIssues(), "'elapsed'");
    }

    @Test
    void arrayLiteralShorterThanTheArrayIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var slots: int[3] := [1, 2]
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "not assignable to variable 'slots'");
    }

    @Test
    void arrayLiteralShorterThanARangedArrayIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var slots: int[1..3] := [1, 2]
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyMissingInitializers(parsedPackage.getIssues(), "gives 2 of the 3 slots");
    }

    @Test
    void arrayLiteralFillingEverySlotIsAccepted() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var slots: int[3] := [1, 2, 3]
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void loopVariablesAndParametersNeedNoInitializer() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran add(amount: int) {
                    count := count + amount
                }
                redefine tran {
                    inline for (step in 0..2) {
                        inline add(step)
                    }
                    for (index in 0..2) {
                        count := count + index
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void inlinedModelVariableWithoutInitializerIsRejected() throws Exception {
        var inlinedOxsts = inlinedParseHelper.parse("""
            inlined oxsts of Counter
            var count: int
            init {
            }
            tran {
            }
            prop {
                return AG true
            }
            """);

        var issues = resourceValidator.validate(inlinedOxsts.eResource(), CheckMode.ALL, CancelIndicator.NullImpl);

        assertThat(issues)
                .filteredOn(issue -> OxstsValidator.MISSING_INITIALIZER.equals(issue.getCode()))
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getMessage()).contains("'count'");
                });
    }

    private static void assertOnlyMissingInitializers(List<Issue> issues, String messageSubstring) {
        assertThat(issues)
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.MISSING_INITIALIZER);
                    assertThat(issue.getMessage()).contains(messageSubstring);
                });
    }
}
