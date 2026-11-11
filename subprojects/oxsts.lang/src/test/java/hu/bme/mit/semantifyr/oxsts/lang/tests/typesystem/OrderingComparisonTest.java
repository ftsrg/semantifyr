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
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class OrderingComparisonTest {

    private static final String HOLDER = """
            package test
            enum Color {
                Red,
                Green,
            }
            class Holder {
                var color: Color := Color::Red
                var count: int := 0
                var flag: bool := false
                var label: string := "a"
                prop {
                    return AG %s
                }
            }
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void orderingOnNumbersPasses() {
        var parsedPackage = parseHelper.parse(HOLDER.formatted("count <= 3"), CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void equalityOnEnumsPasses() {
        var parsedPackage = parseHelper.parse(HOLDER.formatted("color != Color::Green"), CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void orderingOnEnumsIsRejected() {
        assertOnlyOrderingError(HOLDER.formatted("Color::Red < Color::Green"), "Color and Color");
    }

    @Test
    void orderingOnBooleansIsRejected() {
        assertOnlyOrderingError(HOLDER.formatted("flag >= true"), "bool and bool");
    }

    @Test
    void orderingOnStringsIsRejected() {
        assertOnlyOrderingError(HOLDER.formatted("label > \"b\""), "string and string");
    }

    private void assertOnlyOrderingError(String source, String operandTypes) {
        var parsedPackage = parseHelper.parse(source, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, operandTypes);
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> assertThat(issue.getLineNumber()).isEqualTo(12));
    }
}
