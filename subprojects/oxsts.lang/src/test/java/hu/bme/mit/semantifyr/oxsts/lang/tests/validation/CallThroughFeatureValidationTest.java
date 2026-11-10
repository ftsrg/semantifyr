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
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class CallThroughFeatureValidationTest {

    private static final String WORKERS = """
            package test
            class Worker {
                var done: bool := false
                tran step() {
                    done := true
                }
                prop isDone(): bool {
                    return done
                }
            }
            class Team {
                contains workers: Worker[0..*]
                contains first: Worker subsets workers
                contains second: Worker subsets workers
                contains spare: Worker[0..1]
            }
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void callsThroughSingleValuedFeaturesAndLoopVariablesPass() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Host {
                contains team: Team
                redefine tran {
                    inline team.first.step()
                    inline team.spare?.step()
                    inline for (worker in team.workers) {
                        inline worker.step()
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void inlineCallThroughAManyValuedFeatureIsRejected() {
        var model = """
            class Host {
                contains team: Team
                redefine tran {
                    inline team.workers.step()
                }
            }
            """;

        assertOnlyErrorAt(WORKERS + model, "team.workers.step");
    }

    @Test
    void propertyCallThroughAManyValuedFeatureIsRejected() {
        var model = """
            class Lead : Team {
                prop {
                    return EF workers.isDone()
                }
            }
            """;

        assertOnlyErrorAt(WORKERS + model, "workers.isDone");
    }

    private void assertOnlyErrorAt(String source, String navigationText) {
        var parsedPackage = parseHelper.parse(source, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.CALL_THROUGH_MANY_VALUED_FEATURE, "'workers'");
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.CALL_THROUGH_MANY_VALUED_FEATURE);
                    assertThat(issue.getOffset()).isEqualTo(source.indexOf(navigationText));
                });
    }
}
