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
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                var i: int := 0
                redefine tran {
                    var values: int[2] := [1, x]
                    var scratch: int[3]
                    scratch[i] := values[1]
                    x := scratch[i]
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void localClassTypedArrayIsRejectedOnce() {
        var pkg = parseHelper.parse(WORKERS + """
            class C {
                contains a: Worker
                redefine tran {
                    var slots: Worker[2]
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertThat(pkg.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> assertThat(issue.getMessage()).contains("Arrays of class or feature type"));
    }

    @Test
    void parameterArrayIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran set(values: int[2]) {
                    x := values[1]
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "Array parameter");
        assertOnlyErrorsAreUnsupportedAtLine(pkg, 4);
    }

    @Test
    void optionalAndUnboundedParametersAreNotArrays() {
        var pkg = parseHelper.parse(WORKERS + """
            class C {
                contains workers: Worker[0..*]
                tran stepAll(all: Worker[0..*]) {
                    inline for (worker in all) {
                        inline worker.step()
                    }
                }
                tran stepOne(one: Worker[0..1]) { }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void readThroughClassTypedVariableIsRejected() {
        var pkg = parseHelper.parse(WORKERS + """
            class C {
                contains a: Worker
                var current: Worker[0..1] := nothing
                prop { return EF current.done }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "through variable 'current'");
        assertOnlyErrorsAreUnsupportedAtLine(pkg, 15);
    }

    @Test
    void writeThroughFeatureTypedVariableIsRejected() {
        var pkg = parseHelper.parse(WORKERS + """
            class C {
                contains workers: Worker[0..*]
                contains a: Worker subsets workers
                var current: workers[0..1] := nothing
                redefine tran { current.done := true }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "through variable 'current'");
        assertOnlyErrorsAreUnsupportedAtLine(pkg, 16);
    }

    @Test
    void optionalNavigationThroughVariableIsRejected() {
        var pkg = parseHelper.parse(WORKERS + """
            class C {
                contains a: Worker
                var current: Worker[0..1] := nothing
                prop { return EF current?.done }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "through variable 'current'");
    }

    @Test
    void featureAccessThroughVariableIsRejectedEvenWhenCalled() {
        var pkg = parseHelper.parse(WORKERS + """
            class C {
                contains a: Worker
                var current: Worker[0..1] := a
                redefine tran { inline current.part.step() }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "through variable 'current'");
    }

    @Test
    void accessThroughLocalVariableIsRejected() {
        var pkg = parseHelper.parse(WORKERS + """
            class C {
                contains a: Worker
                var seen: bool := false
                redefine tran {
                    var current: Worker[0..1] := a
                    seen := current.done
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "through variable 'current'");
    }

    @Test
    void accessThroughRuntimeForLoopVariableIsRejected() {
        var pkg = parseHelper.parse(WORKERS + """
            class C {
                contains workers: Worker[0..*]
                contains a: Worker subsets workers
                redefine tran {
                    for (worker in workers) {
                        worker.done := true
                    }
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "through variable 'worker'");
    }

    @Test
    void callsThroughVariablesPass() {
        var pkg = parseHelper.parse(WORKERS + """
            class C {
                contains workers: Worker[0..*]
                contains a: Worker subsets workers
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

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void accessThroughInlineForLoopVariablePasses() {
        var pkg = parseHelper.parse(WORKERS + """
            class C {
                contains workers: Worker[0..*]
                contains a: Worker subsets workers
                redefine tran {
                    inline for (worker in workers) {
                        worker.done := true
                        worker.part.done := true
                    }
                }
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
}
