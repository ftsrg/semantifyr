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
public class ClockValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void clockIsResetAndComparedWithIntegersByEveryOperator() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Timer {
                refers limit: int = 5
                var elapsed: clock := 0
                redefine tran {
                    assume(elapsed <= limit)
                    assume(elapsed >= 1)
                    assume(elapsed == 2)
                    assume(3 >= elapsed)
                    assume(!(elapsed < 1))
                    assume(!(elapsed > 4))
                    assume(!(elapsed != 2))
                    elapsed := 0
                    assume(elapsed < 1)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void arithmeticOnAClockIsRejected() {
        assertOnlyClockError("""
                assume(elapsed + 1 <= 5)
            """, "Arithmetic on a clock");
    }

    @Test
    void havocOfAClockIsRejected() {
        assertOnlyClockError("""
                havoc(elapsed)
            """, "cannot be havocked");
    }

    @Test
    void resetToAnythingButZeroIsRejected() {
        assertOnlyClockError("""
                elapsed := 1
            """, "reset only to 0");
    }

    @Test
    void clockInitializerOtherThanZeroIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Timer {
                var elapsed: clock := 3
            }
            """, CheckMode.ALL);

        assertOnlyClockError(parsedPackage, "initializer is 0");
    }

    @Test
    void comparisonOfTwoClocksIsRejected() {
        assertOnlyClockError("""
                assume(elapsed <= other)
            """, "Two clocks cannot be compared");
    }

    @Test
    void comparisonWithANonIntegerIsRejected() {
        assertOnlyClockError("""
                assume(elapsed == true)
            """, "compared with an integer, found bool");
    }

    @Test
    void clockReadOutsideAComparisonIsRejected() {
        assertOnlyClockError("""
                assume(elapsed)
            """, "read only in a comparison");
    }

    @Test
    void clockArgumentIsRejected() {
        assertOnlyClockError("""
                inline observe(elapsed)
            """, "cannot be passed as an argument");
    }

    @Test
    void clockParameterIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Timer {
                tran restart(timer: clock) {
                }
            }
            """, CheckMode.ALL);

        assertOnlyClockError(parsedPackage, "A parameter cannot be a clock");
    }

    @Test
    void clockRecordFieldIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            record Stamp {
                var elapsed: clock := 0
            }
            """, CheckMode.ALL);

        assertOnlyClockError(parsedPackage, "A record field cannot be a clock");
    }

    @Test
    void clockArrayIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Timer {
                var elapsed: clock[2] := [0, 0]
            }
            """, CheckMode.ALL);

        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .anySatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.INCORRECT_CLOCK_USE);
                    assertThat(issue.getMessage()).contains("cannot be an array element");
                });
    }

    @Test
    void propertyReadingAClockIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Timer {
                var elapsed: clock := 0
                prop {
                    return AG elapsed <= 5
                }
            }
            """, CheckMode.ALL);

        assertOnlyClockError(parsedPackage, "The verified property cannot read a clock");
    }

    @Test
    void propertyReadingAClockForAnAssumptionPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Timer {
                var elapsed: clock := 0
                prop isUp(): bool {
                    return elapsed >= 5
                }
                redefine tran {
                    assume(isUp())
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void localClockIsRejected() {
        assertOnlyClockError("""
                var lap: clock := 0
            """, "never as a local");
    }

    @Test
    void clockFeatureIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Timer {
                refers elapsed: clock
            }
            """, CheckMode.ALL);

        assertOnlyClockError(parsedPackage, "never as a feature");
    }

    @Test
    void clockTestsThatAreNotClosedAreWarned() {
        var parsedPackage = parseHelper.parse(TIMER + """
                redefine tran {
                    if (elapsed <= 5) {
                        flag := true
                    }
                    assume(elapsed < 5)
                    assume(elapsed != 5)
                    assume(!(elapsed <= 5))
                    assume((elapsed <= 5) ^^ flag)
                    flag := elapsed >= 5
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .isEmpty();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> OxstsValidator.CLOCK_TEST_NOT_CLOSED.equals(issue.getCode()))
                .extracting(issue -> issue.getLineNumber())
                .containsExactly(7, 10, 11, 12, 13, 14);
    }

    @Test
    void clockTestsInInitAndAfterAResetOnEveryPathAreExempt() {
        var parsedPackage = parseHelper.parse(TIMER + """
                redefine init {
                    if (elapsed < 1) {
                        flag := true
                    }
                }
                redefine tran {
                    choice {
                        elapsed := 0
                    } or {
                        elapsed := 0
                        other := 0
                    }
                    if (elapsed < 1) {
                        flag := true
                    }
                    if (other < 1) {
                        flag := false
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> OxstsValidator.CLOCK_TEST_NOT_CLOSED.equals(issue.getCode()))
                .extracting(issue -> issue.getLineNumber())
                .containsExactly(21);
    }

    private static final String TIMER = """
            package test
            class Timer {
                var elapsed: clock := 0
                var other: clock := 0
                var flag: bool := false
            """;

    private void assertOnlyClockError(String body, String messageFragment) {
        var parsedPackage = parseHelper.parse(TIMER + """
                tran observe(value: int) {
                }
                redefine tran {
            """ + body + """
                }
            }
            """, CheckMode.ALL);

        assertOnlyClockError(parsedPackage, messageFragment);
    }

    private void assertOnlyClockError(WrappedOxstsPackage parsedPackage, String messageFragment) {
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.INCORRECT_CLOCK_USE);
                    assertThat(issue.getMessage()).contains(messageFragment);
                });
    }
}
