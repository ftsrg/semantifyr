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
public class AnnotationTargetValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void builtinAnnotationsOnTheirTargetsPass() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            @VerificationCase
            @Tag("slow")
            class Case {
                @Shared
                contains item: Item
                @Control
                @Clock
                @NonOptimizable
                var count: int := 0
                @Trace
                tran step() {
                    count := count + 1
                }
                prop {
                    return AG count >= 0
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void builtinAnnotationsOffTheirTargetsAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            @Control
            class Holder {
                @VerificationCase
                var count: int := 0
                @Shared
                refers other: Item[0..1]
                @Trace
                prop positive(): bool {
                    return count > 0
                }
                @Tag("slow")
                tran step() {
                    count := count + 1
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> {
                    return issue.getSeverity() == Severity.ERROR;
                })
                .extracting(issue -> {
                    return issue.getCode() + "@" + issue.getLineNumber();
                })
                .containsExactlyInAnyOrder(
                        OxstsValidator.INCORRECT_ANNOTATION_TARGET + "@3",
                        OxstsValidator.INCORRECT_ANNOTATION_TARGET + "@5",
                        OxstsValidator.INCORRECT_ANNOTATION_TARGET + "@7",
                        OxstsValidator.INCORRECT_ANNOTATION_TARGET + "@9",
                        OxstsValidator.INCORRECT_ANNOTATION_TARGET + "@13");
        parsedPackage.assertHasValidationIssue(
                OxstsValidator.INCORRECT_ANNOTATION_TARGET, "@Shared applies to containment features only");
    }

    @Test
    void userAnnotationsMayGoAnywhere() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Note
            @Note
            class Holder {
                @Note
                var count: int := 0
                @Note
                tran step() {
                    count := count + 1
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }
}
