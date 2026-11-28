/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.validation;

import static hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@InjectWithOxsts
public class CallThroughFeatureValidationTest {

    private static final String WORKERS = testModel(CallThroughFeatureValidationTest.class, "workers.semantifyr");

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
                redefine prop {
                    return EF workers.isDone()
                }
            }
            """;

        assertOnlyErrorAt(WORKERS + model, "workers.isDone");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            nullValues = "none",
            value = {
                "contains held: Worker        |                      | held.step()  | none",
                "contains held: Worker        |                      | held?.step() | OXSTS.INCORRECT_CALL_HOLDER_MULTIPLICITY",
                "contains held: Worker[1]     |                      | held.step()  | none",
                "contains held: Worker[0..1]  |                      | held.step()  | OXSTS.INCORRECT_CALL_HOLDER_MULTIPLICITY",
                "contains held: Worker[0..1]  |                      | held?.step() | none",
                "contains held: Worker[0..*]  |                      | held?.step() | OXSTS.CALL_THROUGH_MANY_VALUED_FEATURE",
                "refers held: Worker[]        |                      | held?.step() | OXSTS.CALL_THROUGH_MANY_VALUED_FEATURE",
                "var held: Worker := lead     |                      | held.step()  | none",
                "var held: Worker := lead     |                      | held?.step() | OXSTS.INCORRECT_CALL_HOLDER_MULTIPLICITY",
                "var held: Worker[0..1] := nothing |                 | held.step()  | OXSTS.INCORRECT_CALL_HOLDER_MULTIPLICITY",
                "var held: Worker[0..1] := nothing |                 | held?.step() | none",
                "                             | held: Worker         | held.step()  | none",
                "                             | held: Worker         | held?.step() | OXSTS.INCORRECT_CALL_HOLDER_MULTIPLICITY",
                "                             | held: Worker[0..1]   | held.step()  | OXSTS.INCORRECT_CALL_HOLDER_MULTIPLICITY",
                "                             | held: Worker[0..1]   | held?.step() | none",
            })
    void inlineCallHolderFollowsItsMultiplicity(String member, String parameter, String call, String expectedCode) {
        var source = WORKERS + """
            class Host {
                contains lead: Worker
                %s
                tran act(%s) {
                    inline %s
                }
            }
            """.formatted(member == null ? "" : member, parameter == null ? "" : parameter, call);
        var parsedPackage = parseHelper.parse(source, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        var errorCodes = parsedPackage.getIssues().stream()
                .filter(issue -> issue.getSeverity() == Severity.ERROR)
                .map(issue -> issue.getCode())
                .toList();
        if (expectedCode == null) {
            assertThat(errorCodes).isEmpty();
        } else {
            assertThat(errorCodes).containsExactly(expectedCode);
        }
    }

    @Test
    void propertyCallThroughAnOptionalFeatureWithADotPasses() {
        var parsedPackage = parseHelper.parse(WORKERS + """
            class Lead : Team {
                redefine prop {
                    return EF spare.isDone()
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
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
