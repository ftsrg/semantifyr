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
import java.util.List;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.Issue;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ConstantExpressionHintValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void anArithmeticConstantIsHintedOnceOnTheWholeExpression() {
        var issues = constantHints("""
                package test

                class Counter {
                    var count: int := 0

                    redefine tran {
                        count := 2 * 3 + 1
                    }
                }
                """);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.getSeverity()).isEqualTo(Severity.INFO);
            assertThat(issue.getMessage()).isEqualTo("The expression is constant, it can be inlined as its value.");
            assertThat(issue.getLineNumber()).isEqualTo(7);
            assertThat(issue.getColumn()).isEqualTo(18);
            assertThat(issue.getLength()).isEqualTo("2 * 3 + 1".length());
        });
    }

    @Test
    void aBooleanConstantIsHinted() {
        var issues = constantHints("""
                package test

                class Counter {
                    var count: int := 0

                    redefine tran {
                        assume(true && !false)
                        count := 1
                    }
                }
                """);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.getLineNumber()).isEqualTo(7);
            assertThat(issue.getLength()).isEqualTo("true && !false".length());
        });
    }

    @Test
    void aConstantInsideAFeatureBindingIsHinted() {
        var issues = constantHints("""
                package test

                class Counter {
                    refers size: int = 2 + 2
                }
                """);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.getLineNumber()).isEqualTo(4);
            assertThat(issue.getColumn()).isEqualTo(24);
        });
    }

    @Test
    void aConstantBoundOfARangeIsHintedButNotTheRange() {
        var issues = constantHints("""
                package test

                class Counter {
                    var count: int := 0

                    redefine tran {
                        inline for (index in 0..(1 + 1)) {
                            count := count + index
                        }
                    }
                }
                """);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.getLength()).isEqualTo("1 + 1".length());
        });
    }

    @Test
    void literalsReferencesRangesAndNonConstantExpressionsAreNotHinted() {
        var issues = constantHints("""
                package test

                enum LightColor {
                    Red,
                    Green
                }

                class Counter {
                    refers size: int = 3
                    refers offset: int = -3
                    refers doubleSize: int = size * 2
                    var color: LightColor := LightColor::Red
                    var count: int := 0

                    redefine tran {
                        count := size
                        count := count + 1
                        color := LightColor::Green
                        inline for (index in 0..2) {
                            count := index
                        }
                    }
                }
                """);

        assertThat(issues).isEmpty();
    }

    private List<Issue> constantHints(String text) {
        var parsedPackage = parseHelper.parse(text, CheckMode.ALL);
        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
        return parsedPackage.getIssues().stream()
                .filter(issue -> {
                    return OxstsValidator.CONSTANT_EXPRESSION.equals(issue.getCode());
                })
                .toList();
    }
}
