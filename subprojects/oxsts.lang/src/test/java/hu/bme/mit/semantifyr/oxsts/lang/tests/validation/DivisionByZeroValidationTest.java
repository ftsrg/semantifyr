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
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.Issue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@InjectWithOxsts
public class DivisionByZeroValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @ParameterizedTest
    @ValueSource(strings = {"10 / 0", "10 / (2 - 2)", "divisor + 10 / 0", "10.0 / 0.0"})
    void aDivisorThatIsZeroAsAConstantIsRejected(String expression) {
        assertThat(divisionIssues(expression)).singleElement().satisfies(issue -> {
            assertThat(issue.getMessage()).isEqualTo("The divisor is zero.");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"10 / 2", "10 / divisor", "divisor / 1", "10 / (divisor - divisor)"})
    void aDivisorThatIsNonzeroOrReadsTheStateIsAccepted(String expression) {
        assertThat(divisionIssues(expression)).isEmpty();
    }

    private List<Issue> divisionIssues(String expression) {
        var parsedPackage = parseHelper.parse("""
            package test
            class Ratio {
                var divisor: int := 0
                var result: real := 0.0

                redefine tran {
                    result := %s
                }
            }
            """.formatted(expression), CheckMode.ALL);
        parsedPackage.assertNoResourceErrors();
        return parsedPackage.getIssues().stream()
                .filter(it -> OxstsValidator.DIVISION_BY_ZERO.equals(it.getCode()))
                .toList();
    }
}
