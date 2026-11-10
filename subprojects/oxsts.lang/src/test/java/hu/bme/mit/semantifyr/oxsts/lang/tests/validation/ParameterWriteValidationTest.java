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
public class ParameterWriteValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void assignmentToAParameterIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran add(amount: int) {
                    amount := amount + 1
                    count := count + amount
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyParameterWriteAt(parsedPackage, 5, "'amount'");
    }

    @Test
    void havocOfAParameterIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran add(amount: int) {
                    havoc(amount)
                    count := count + amount
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyParameterWriteAt(parsedPackage, 5, "'amount'");
    }

    @Test
    void assignmentToAParameterInANestedBlockIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran add(amount: int) {
                    choice {
                        amount := 0
                    } or {
                        count := amount
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyParameterWriteAt(parsedPackage, 6, "'amount'");
    }

    @Test
    void writingAVariableThroughAParameterValuePasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran set(amount: int) {
                    var local: int := amount
                    local := local + 1
                    count := local
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    private static void assertOnlyParameterWriteAt(WrappedOxstsPackage parsedPackage, int line, String name) {
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.INCORRECT_ASSIGNMENT);
                    assertThat(issue.getLineNumber()).isEqualTo(line);
                    assertThat(issue.getMessage()).contains(name).contains("passed by value");
                });
    }
}
