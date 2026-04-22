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
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.Issue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@InjectWithOxsts
public class DiagnosticSuppressionTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void arithmeticErrorDoesNotCascadeToAssignment() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                var b: bool := false
                redefine tran {
                    x := b + 1
                }
            }
            """, CheckMode.ALL);

        var typeMismatches = pkg.getIssues().stream()
            .filter(i -> i.getSeverity() == Severity.ERROR)
            .filter(i -> OxstsValidator.TYPE_MISMATCH.equals(i.getCode()))
            .toList();
        assertThat(typeMismatches)
            .as("exactly one type-mismatch error should be reported for the inner arithmetic operand, " +
                "not a cascade to the enclosing assignment")
            .hasSize(1);
        assertThat(typeMismatches.getFirst().getMessage())
            .contains("arithmetic");
    }

    @Test
    void booleanErrorDoesNotCascadeToAG() {
        var pkg = parseHelper.parse("""
            package test
            @VerificationCase
            class C {
                var b: bool := true
                var n: int := 0
                redefine tran { n := n + 1 }
                prop { return AG (b && n) }
            }
            """, CheckMode.ALL);

        var typeMismatches = pkg.getIssues().stream()
            .filter(i -> i.getSeverity() == Severity.ERROR)
            .filter(i -> OxstsValidator.TYPE_MISMATCH.equals(i.getCode()))
            .toList();
        assertThat(typeMismatches)
            .as("only the innermost bool-operand error should fire")
            .hasSize(1);
        assertThat(typeMismatches.getFirst().getMessage())
            .contains("boolean");
    }

    @Test
    void ifGuardErrorDoesNotCascadeToBranches() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var n: int := 0
                var result: int := 0
                redefine tran {
                    result := if n then 1 else 2
                }
            }
            """, CheckMode.ALL);

        var typeMismatches = pkg.getIssues().stream()
            .filter(i -> i.getSeverity() == Severity.ERROR)
            .filter(i -> OxstsValidator.TYPE_MISMATCH.equals(i.getCode()))
            .toList();
        assertThat(typeMismatches)
            .as("only the guard type error should fire, not the branch compatibility check")
            .hasSize(1);
        assertThat(typeMismatches.getFirst().getMessage())
            .contains("guard");
    }

    @Test
    void comparisonErrorDoesNotCascadeToBooleanOperator() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                var x: int := 0
                var r: bool := false
                redefine tran {
                    r := b && (x < true)
                }
            }
            """, CheckMode.ALL);

        var typeMismatches = pkg.getIssues().stream()
            .filter(i -> i.getSeverity() == Severity.ERROR)
            .filter(i -> OxstsValidator.TYPE_MISMATCH.equals(i.getCode()))
            .toList();
        assertThat(typeMismatches)
            .as("only the innermost comparison error should fire")
            .hasSize(1);
        assertThat(typeMismatches.getFirst().getMessage())
            .containsIgnoringCase("compare");
    }
}
