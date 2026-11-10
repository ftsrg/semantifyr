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
public class UnsupportedFeatureTest {

    private static final String WORKERS = """
            package test
            class Worker {
                var done: bool := false
                contains part: Part
                tran step() { done := true }
                prop isDone(): bool { return done }
            }
            class Part {
                var done: bool := false
                tran step() { done := true }
            }
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void localArrayPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                var position: int := 0
                redefine tran {
                    var values: int[2] := [1, value]
                    var scratch: int[3] := [0, 0, 0]
                    scratch[position] := values[1]
                    value := scratch[position]
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void localFeatureTypedArrayIsAccepted() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Holder {
                contains workers: Worker[0..*]
                contains lead: Worker subsets workers
                redefine tran {
                    var slots: workers[2] := [lead, lead]
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void parameterArrayIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                tran set(values: int[2]) {
                    value := values[1]
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "Array parameter");
        assertOnlyErrorsAreUnsupportedAtLine(parsedPackage, 4);
    }

    @Test
    void optionalAndUnboundedParametersAreNotArrays() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Team {
                contains workers: Worker[0..*]
                tran stepAll(all: Worker[0..*]) {
                    inline for (worker in all) {
                        inline worker.step()
                    }
                }
                tran stepOne(one: Worker[0..1]) { }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void readsThroughRuntimeHoldersPass() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Team {
                contains workers: Worker[0..*]
                contains lead: Worker subsets workers
                var current: Worker[0..1] := nothing
                var seen: bool := false
                redefine tran {
                    var picked: Worker[0..1] := lead
                    seen := current.done || picked.done || current.part.done
                    for (worker in workers) {
                        seen := worker.done
                    }
                }
                prop {
                    return EF current.done
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void callThroughAChainOfAVariablePasses() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Team {
                contains lead: Worker
                var current: Worker[0..1] := lead
                redefine tran {
                    inline current.part.step()
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void writeThroughFeatureTypedVariableIsRejected() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Team {
                contains workers: Worker[0..*]
                contains lead: Worker subsets workers
                var current: workers[0..1] := nothing
                redefine tran {
                    current.done := true
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(
                OxstsValidator.UNSUPPORTED_FEATURE, "Writing through variable 'current'");
        assertOnlyErrorsAreUnsupportedAtLine(parsedPackage, 17);
    }

    @Test
    void havocThroughAChainOfAVariableIsRejected() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Team {
                contains lead: Worker
                var current: Worker[0..1] := nothing
                redefine tran {
                    havoc(current.part.done)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(
                OxstsValidator.UNSUPPORTED_FEATURE, "Writing through variable 'current'");
        assertOnlyErrorsAreUnsupportedAtLine(parsedPackage, 16);
    }

    @Test
    void writeThroughRuntimeForLoopVariableIsRejected() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Team {
                contains workers: Worker[0..*]
                contains lead: Worker subsets workers
                redefine tran {
                    for (worker in workers) {
                        worker.done := true
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "Writing through variable 'worker'");
        assertOnlyErrorsAreUnsupportedAtLine(parsedPackage, 17);
    }

    @Test
    void optionalReadThroughVariableIsRejected() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Team {
                contains lead: Worker
                var current: Worker[0..1] := nothing
                prop {
                    return EF current?.done
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "'?.' through variable 'current'");
        assertOnlyErrorsAreUnsupportedAtLine(parsedPackage, 16);
    }

    @Test
    void optionalCallThroughVariableIsRejected() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Team {
                contains lead: Worker
                var current: Worker[0..1] := nothing
                redefine tran {
                    havoc(current)
                    inline current?.step()
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "'?.' through variable 'current'");
        assertOnlyErrorsAreUnsupportedAtLine(parsedPackage, 17);
    }

    @Test
    void callsThroughVariablesPass() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Team {
                contains workers: Worker[0..*]
                contains lead: Worker subsets workers
                var current: Worker[0..1] := nothing
                redefine tran {
                    havoc(current)
                    inline current.step()
                    for (worker in workers) {
                        inline worker.step()
                    }
                }
                prop { return EF current.isDone() }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void accessThroughInlineForLoopVariablePasses() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Team {
                contains workers: Worker[0..*]
                contains lead: Worker subsets workers
                redefine tran {
                    inline for (worker in workers) {
                        worker.done := true
                        worker.part.done := true
                    }
                }
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
}
