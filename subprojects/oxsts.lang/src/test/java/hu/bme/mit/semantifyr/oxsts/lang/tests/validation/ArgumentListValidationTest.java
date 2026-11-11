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
public class ArgumentListValidationTest {

    private static final String COUNTER = """
            package test
            class Counter {
                var count: int := 0
                tran add(first: int, second: int[0..1], third: int[0..1]) {
                    count := count + first
                }
                redefine tran {
                    %s
                }
            }
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void namedArgumentsAfterPositionalOnesPass() {
        var parsedPackage = parseHelper.parse(COUNTER.formatted("inline add(1, third = 3, second = 2)"), CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void parameterNamedTwiceIsRejectedAtTheSecondArgument() {
        var source = COUNTER.formatted("inline add(first = 1, first = 2)");

        assertOnlyErrorAt(source, OxstsValidator.PARAMETER_BOUND_TWICE, "first = 2");
    }

    @Test
    void parameterBoundByPositionAndByNameIsRejectedAtTheNamedArgument() {
        var source = COUNTER.formatted("inline add(1, first = 2)");

        assertOnlyErrorAt(source, OxstsValidator.PARAMETER_BOUND_TWICE, "first = 2");
    }

    @Test
    void positionalArgumentAfterANamedOneIsRejected() {
        var source = COUNTER.formatted("inline add(second = 1, 2)");

        assertOnlyErrorAt(source, OxstsValidator.POSITIONAL_ARGUMENT_AFTER_NAMED, "2)");
    }

    @Test
    void positionalArgumentBetweenNamedOnesIsRejected() {
        var source = COUNTER.formatted("inline add(first = 1, 2, second = 2)");

        assertOnlyErrorAt(source, OxstsValidator.POSITIONAL_ARGUMENT_AFTER_NAMED, "2, second");
    }

    @Test
    void requiredParameterLeftUnboundBesideNamedOptionalOnesIsRejected() {
        var parsedPackage = parseHelper.parse(COUNTER.formatted("inline add(second = 2)"), CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CALL_ARGUMENTS_COUND, "first");
    }

    @Test
    void positionalAnnotationArgumentAfterANamedOneIsRejected() {
        var source = """
                package test
                annotation Pair(first: int, second: bool)
                @Pair(first = 1, true)
                class Annotated {
                }
                """;

        assertOnlyErrorAt(source, OxstsValidator.POSITIONAL_ARGUMENT_AFTER_NAMED, "true)");
    }

    @Test
    void annotationParameterNamedTwiceIsRejected() {
        var source = """
                package test
                annotation Pair(first: int, second: int[0..1])
                @Pair(second = 1, second = 2)
                class Annotated {
                }
                """;

        assertOnlyErrorAt(source, OxstsValidator.PARAMETER_BOUND_TWICE, "second = 2");
    }

    private void assertOnlyErrorAt(String source, String code, String argumentText) {
        var parsedPackage = parseHelper.parse(source, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(code);
                    assertThat(issue.getOffset()).isEqualTo(source.indexOf(argumentText));
                });
    }
}
