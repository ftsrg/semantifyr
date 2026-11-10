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
public class VariableAnnotationValidationTest {

    private static final String ANNOTATIONS = """
            package test
            annotation Note(text: string)
            annotation Weight(value: int)
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void stringArgumentOnAClassOrATransitionPasses() {
        var parsedPackage = parseHelper.parse(ANNOTATIONS + """
            @Note("a counter")
            class Counter {
                var count: int := 0
                @Note(text = "counts")
                tran increment() {
                    count := count + 1
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void nonStringArgumentOnAVariablePasses() {
        var parsedPackage = parseHelper.parse(ANNOTATIONS + """
            class Counter {
                @Weight(2)
                var count: int := 0
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void stringArgumentOnAVariableIsRejected() {
        var source = ANNOTATIONS + """
            class Counter {
                @Note("a counter")
                var count: int := 0
            }
            """;

        assertOnlyErrorAt(source, "@Note");
    }

    private void assertOnlyErrorAt(String source, String annotationText) {
        var parsedPackage = parseHelper.parse(source, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.UNSUPPORTED_FEATURE);
                    assertThat(issue.getMessage()).contains("'@Note'", "string argument");
                    assertThat(issue.getOffset()).isEqualTo(source.indexOf(annotationText));
                });
    }
}
