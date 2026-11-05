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
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class DiagnosticSuppressionTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void arithmeticErrorDoesNotCascadeToAssignment() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var counter: int := 0
                var flag: bool := false
                redefine tran {
                    counter := flag + 1
                }
            }
            """, CheckMode.ALL);

        var typeMismatches = parsedPackage.getIssues().stream()
                .filter(issue -> issue.getSeverity() == Severity.ERROR)
                .filter(issue -> OxstsValidator.TYPE_MISMATCH.equals(issue.getCode()))
                .toList();
        assertThat(typeMismatches)
                .as("exactly one type-mismatch error should be reported for the inner arithmetic operand, "
                        + "not a cascade to the enclosing assignment")
                .hasSize(1);
        assertThat(typeMismatches.getFirst().getMessage()).contains("arithmetic");
    }

    @Test
    void booleanErrorDoesNotCascadeToAG() {
        var parsedPackage = parseHelper.parse("""
            package test
            @VerificationCase
            class Holder {
                var flag: bool := true
                var counter: int := 0
                redefine tran { counter := counter + 1 }
                prop { return AG (flag && counter) }
            }
            """, CheckMode.ALL);

        var typeMismatches = parsedPackage.getIssues().stream()
                .filter(issue -> issue.getSeverity() == Severity.ERROR)
                .filter(issue -> OxstsValidator.TYPE_MISMATCH.equals(issue.getCode()))
                .toList();
        assertThat(typeMismatches)
                .as("only the innermost bool-operand error should fire")
                .hasSize(1);
        assertThat(typeMismatches.getFirst().getMessage()).contains("boolean");
    }

    @Test
    void ifGuardErrorDoesNotCascadeToBranches() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var counter: int := 0
                var result: int := 0
                redefine tran {
                    result := if counter then 1 else 2
                }
            }
            """, CheckMode.ALL);

        var typeMismatches = parsedPackage.getIssues().stream()
                .filter(issue -> issue.getSeverity() == Severity.ERROR)
                .filter(issue -> OxstsValidator.TYPE_MISMATCH.equals(issue.getCode()))
                .toList();
        assertThat(typeMismatches)
                .as("only the guard type error should fire, not the branch compatibility check")
                .hasSize(1);
        assertThat(typeMismatches.getFirst().getMessage()).contains("guard");
    }

    @Test
    void comparisonErrorDoesNotCascadeToBooleanOperator() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                var counter: int := 0
                var result: bool := false
                redefine tran {
                    result := flag && (counter < true)
                }
            }
            """, CheckMode.ALL);

        var typeMismatches = parsedPackage.getIssues().stream()
                .filter(issue -> issue.getSeverity() == Severity.ERROR)
                .filter(issue -> OxstsValidator.TYPE_MISMATCH.equals(issue.getCode()))
                .toList();
        assertThat(typeMismatches)
                .as("only the innermost comparison error should fire")
                .hasSize(1);
        assertThat(typeMismatches.getFirst().getMessage()).containsIgnoringCase("compare");
    }
}
