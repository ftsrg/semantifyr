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
public class TemporalOperatorPlacementValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void temporalOperatorReturnedByAPropertyPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                redefine tran {
                    flag := true
                }
                prop reached(): bool {
                    return EF flag
                }
                prop {
                    return reached()
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void castTemporalOperatorReturnedByAPropertyPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                prop {
                    return (AG !flag) as bool
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void temporalOperatorInATransitionIsRejected() {
        var source = """
            package test
            class Holder {
                var flag: bool := false
                redefine tran {
                    flag := AG true
                }
                prop {
                    return AG true
                }
            }
            """;

        assertOnlyMisplacedAt(source, "AG true\n");
    }

    @Test
    void temporalOperatorNestedInAnotherIsRejected() {
        var source = """
            package test
            class Holder {
                var flag: bool := false
                prop {
                    return AG (EF flag)
                }
            }
            """;

        assertOnlyMisplacedAt(source, "EF flag");
    }

    @Test
    void temporalOperatorBelowAnotherOperatorIsRejected() {
        var source = """
            package test
            class Holder {
                var flag: bool := false
                prop {
                    return !(EF flag)
                }
            }
            """;

        assertOnlyMisplacedAt(source, "EF flag");
    }

    private void assertOnlyMisplacedAt(String source, String operatorText) {
        var parsedPackage = parseHelper.parse(source, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.MISPLACED_TEMPORAL_OPERATOR);
                    assertThat(issue.getOffset()).isEqualTo(source.indexOf(operatorText));
                });
    }
}
