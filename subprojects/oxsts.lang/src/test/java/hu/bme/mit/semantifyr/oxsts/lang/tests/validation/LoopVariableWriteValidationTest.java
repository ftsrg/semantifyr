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
public class LoopVariableWriteValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void assignmentToARuntimeLoopVariableIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test

            class Counter {
                var count: int := 0

                redefine tran {
                    for (index in 0..count) {
                        index := 0
                    }
                }
            }
            """, CheckMode.ALL);

        assertOnlyLoopVariableWriteAt(parsedPackage, 8);
    }

    @Test
    void havocOfARuntimeLoopVariableIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test

            class Counter {
                var count: int := 0

                redefine tran {
                    for (index in 0..count) {
                        havoc(index)
                    }
                }
            }
            """, CheckMode.ALL);

        assertOnlyLoopVariableWriteAt(parsedPackage, 8);
    }

    @Test
    void assignmentToAnInlineLoopVariableInANestedBlockIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test

            class Counter {
                var count: int := 0

                redefine tran {
                    inline for (index in 0..2) {
                        choice {
                            index := 0
                        } or {
                            count := index
                        }
                    }
                }
            }
            """, CheckMode.ALL);

        assertOnlyLoopVariableWriteAt(parsedPackage, 9);
    }

    @Test
    void havocOfAnInlineChoiceLoopVariableIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test

            class Counter {
                redefine tran {
                    inline for choice (index in 0..2) {
                        havoc(index)
                    }
                }
            }
            """, CheckMode.ALL);

        assertOnlyLoopVariableWriteAt(parsedPackage, 6);
    }

    @Test
    void writingAVariableThroughALoopVariableIsAccepted() {
        var parsedPackage = parseHelper.parse("""
            package test

            class Worker {
                var done: bool := false
            }

            class Factory {
                contains workers: Worker[2]
                var count: int := 0

                redefine tran {
                    inline for (worker in workers) {
                        worker.done := true
                        count := count + 1
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    private static void assertOnlyLoopVariableWriteAt(WrappedOxstsPackage parsedPackage, int line) {
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.INCORRECT_ASSIGNMENT);
                    assertThat(issue.getLineNumber()).isEqualTo(line);
                    assertThat(issue.getMessage())
                            .isEqualTo("Cannot write loop variable 'index': it takes its values from the range.");
                });
    }
}
