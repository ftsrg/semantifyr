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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@InjectWithOxsts
public class MixedBooleanOperatorsValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @ParameterizedTest
    @CsvSource({
        "a || b && c, a || (b && c)",
        "a && b || c, (a && b) || c",
        "a ^^ b && c, a ^^ (b && c)",
        "a || b ^^ c, a || (b ^^ c)",
        "(a) || (b) && c, (a) || ((b) && c)",
    })
    void mixingWithoutParenthesesWarnsWithTheReading(String expression, String reading) {
        assertThat(mixedOperatorIssues(expression)).isNotEmpty().allSatisfy(issue -> {
            assertThat(issue.getSeverity()).isEqualTo(Severity.WARNING);
            assertThat(issue.getMessage()).contains("`&&` binds tighter than `^^`", "`" + reading + "`");
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "a || (b && c)",
                "(a || b) && c",
                "(a && b) || c",
                "a ^^ (b && c)",
                "a || (b ^^ c)",
                "a && b && c",
                "a || b || c",
                "(a || b) && (b ^^ c)",
            })
    void parenthesesOrASingleOperatorDoNotWarn(String expression) {
        assertThat(mixedOperatorIssues(expression)).isEmpty();
    }

    private List<Issue> mixedOperatorIssues(String expression) {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var a: bool := false
                var b: bool := false
                var c: bool := false
                redefine tran { assume(%s) }
            }
            """.formatted(expression), CheckMode.ALL);
        pkg.assertNoResourceErrors();
        return pkg.getIssues().stream()
                .filter(it -> OxstsValidator.MIXED_BOOLEAN_OPERATORS.equals(it.getCode()))
                .toList();
    }
}
