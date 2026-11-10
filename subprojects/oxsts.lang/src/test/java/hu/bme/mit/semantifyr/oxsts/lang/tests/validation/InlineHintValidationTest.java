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
public class InlineHintValidationTest {

    private static final String WORKER = """
            package test

            class Worker {
                var done: bool := false

                tran step() {
                    done := true
                }
            }
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private ParseHelper<InlinedOxsts> inlinedParseHelper;

    @Inject
    private IResourceValidator resourceValidator;

    @Test
    void ifWithConstantGuardIsHintedAtItsKeyword() {
        var issues = inlineHints(WORKER + """

                class Factory {
                    var count: int := 0

                    redefine tran {
                        if (1 < 2) {
                            count := 1
                        } else {
                            count := 2
                        }
                    }
                }
                """);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.getSeverity()).isEqualTo(Severity.INFO);
            assertThat(issue.getMessage())
                    .isEqualTo("`if` could be `inline if`: its guard is evaluable at compile time.");
            assertThat(issue.getLineNumber()).isEqualTo(15);
            assertThat(issue.getColumn()).isEqualTo(9);
            assertThat(issue.getLength()).isEqualTo(2);
        });
    }

    @Test
    void ifWithFeatureGuardIsHinted() {
        var issues = inlineHints(WORKER + """

                class Factory {
                    contains first: Worker[1]
                    contains second: Worker[1]

                    redefine tran {
                        if (first == second) {
                            first.done := true
                        }
                    }
                }
                """);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.getMessage()).startsWith("`if` could be `inline if`");
        });
    }

    @Test
    void forOverAConstantRangeIsHinted() {
        var issues = inlineHints(WORKER + """

                class Factory {
                    var count: int := 0

                    redefine tran {
                        for (index in 0..2) {
                            count := count + index
                        }
                    }
                }
                """);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.getSeverity()).isEqualTo(Severity.INFO);
            assertThat(issue.getMessage())
                    .isEqualTo("`for` could be `inline for`: its range is evaluable at compile time.");
            assertThat(issue.getLength()).isEqualTo(3);
        });
    }

    @Test
    void forOverAFeatureIsHinted() {
        var issues = inlineHints(WORKER + """

                class Factory {
                    contains workers: Worker[2]

                    redefine tran {
                        for (worker in workers) {
                            inline worker.step()
                        }
                    }
                }
                """);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.getMessage()).startsWith("`for` could be `inline for`");
        });
    }

    @Test
    void ifWithRuntimeGuardIsNotHinted() {
        var issues = inlineHints(WORKER + """

                class Factory {
                    var count: int := 0

                    redefine tran {
                        if (count < 2) {
                            count := count + 1
                        }
                    }
                }
                """);

        assertThat(issues).isEmpty();
    }

    @Test
    void forOverARuntimeRangeIsNotHinted() {
        var issues = inlineHints(WORKER + """

                class Factory {
                    var count: int := 0
                    var total: int := 0

                    redefine tran {
                        for (index in 0..count) {
                            total := total + index
                        }
                    }
                }
                """);

        assertThat(issues).isEmpty();
    }

    @Test
    void inlineIfAndInlineForAreNotHinted() {
        var issues = inlineHints(WORKER + """

                class Factory {
                    contains workers: Worker[2]
                    var count: int := 0

                    redefine tran {
                        inline if (1 < 2) {
                            count := 1
                        }
                        inline for (worker in workers) {
                            inline worker.step()
                        }
                        inline for choice (index in 0..2) {
                            count := index
                        }
                    }
                }
                """);

        assertThat(issues).isEmpty();
    }

    @Test
    void choiceIsNotHinted() {
        var issues = inlineHints(WORKER + """

                class Factory {
                    var count: int := 0

                    redefine tran {
                        choice {
                            count := 1
                        } or {
                            count := 2
                        }
                    }
                }
                """);

        assertThat(issues).isEmpty();
    }

    @Test
    void ifInsideAnInlinedModelIsNotHinted() throws Exception {
        var inlinedOxsts = inlinedParseHelper.parse("""
                inlined oxsts of semantifyr::Anything
                var count: int := 0
                init {
                }
                tran {
                    if (true) {
                        count := 1
                    }
                }
                prop {
                    AG true
                }
                """);

        var issues = resourceValidator.validate(inlinedOxsts.eResource(), CheckMode.ALL, CancelIndicator.NullImpl);

        assertThat(issues)
                .filteredOn(issue -> {
                    return OxstsValidator.COULD_BE_INLINE.equals(issue.getCode());
                })
                .isEmpty();
    }

    private List<Issue> inlineHints(String text) {
        var parsedPackage = parseHelper.parse(text, CheckMode.ALL);
        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
        return parsedPackage.getIssues().stream()
                .filter(issue -> {
                    return OxstsValidator.COULD_BE_INLINE.equals(issue.getCode());
                })
                .toList();
    }
}
