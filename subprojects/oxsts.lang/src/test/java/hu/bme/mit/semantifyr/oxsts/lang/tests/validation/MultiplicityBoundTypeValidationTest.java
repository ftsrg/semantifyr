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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@InjectWithOxsts
public class MultiplicityBoundTypeValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "var values: int[true] := [0]",
                "var values: int[1..true] := [0]",
                "var values: int[false..2] := [0, 0]",
                "var elapsed: clock[true] := 0",
                "contains parts: Part[true]",
                "refers limit: int[\"two\"] = 2",
                "var weights: int[1.5] := [0]",
            })
    void aMultiplicityBoundThatIsNoIntegerIsRejected(String declaration) {
        var parsedPackage = parseHelper.parse("""
                package test
                class Part {
                }
                class Host {
                    %s
                }
                """.formatted(declaration), CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .filteredOn(issue -> issue.getMessage().startsWith("A multiplicity bound must be an integer"))
                .singleElement()
                .satisfies(issue -> assertThat(issue.getCode()).isEqualTo(OxstsValidator.TYPE_MISMATCH));
    }

    @Test
    void integerBoundsAndRangesPass() {
        var parsedPackage = parseHelper.parse("""
                package test
                class Part {
                }
                class Host {
                    refers capacity: int = 2
                    contains parts: Part[0..*]
                    var values: int[3] := [0, 0, 0]
                    var slots: int[0..<capacity] := [0, 0]
                    var flags: bool[1..2] := [false, false]
                }
                """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }
}
