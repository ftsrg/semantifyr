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

@InjectWithOxsts
public class AnnotationTargetValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void builtinAnnotationsOnTheirTargetsPass() {
        var parsedPackage =
                parseHelper.parse(testModel(getClass(), "annotations_on_targets.semantifyr"), CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void builtinAnnotationsOffTheirTargetsAreRejected() {
        var parsedPackage =
                parseHelper.parse(testModel(getClass(), "annotations_off_targets.semantifyr"), CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> {
                    return issue.getSeverity() == Severity.ERROR;
                })
                .extracting(issue -> {
                    return issue.getCode() + "@" + issue.getLineNumber();
                })
                .containsExactlyInAnyOrder(
                        OxstsValidator.INCORRECT_ANNOTATION_TARGET + "@11",
                        OxstsValidator.INCORRECT_ANNOTATION_TARGET + "@13",
                        OxstsValidator.INCORRECT_ANNOTATION_TARGET + "@15",
                        OxstsValidator.INCORRECT_ANNOTATION_TARGET + "@17",
                        OxstsValidator.INCORRECT_ANNOTATION_TARGET + "@21");
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
