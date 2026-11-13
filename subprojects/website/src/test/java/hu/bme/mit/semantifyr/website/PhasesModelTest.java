/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class PhasesModelTest {

    private static final Path MODEL = Path.of("../../oxsts-test-models/phases/room.oxsts");

    @Inject
    private ParseHelper<OxstsModelPackage> parseHelper;

    @Inject
    private IResourceValidator resourceValidator;

    @Test
    void theModelOfThePhasesPagesValidatesWithoutAnIssue() throws Exception {
        var model = parseHelper.parse(Files.readString(MODEL));

        var issues = resourceValidator.validate(model.eResource(), CheckMode.ALL, CancelIndicator.NullImpl).stream()
                .filter(issue -> {
                    return issue.getSeverity() == Severity.ERROR || issue.getSeverity() == Severity.WARNING;
                })
                .map(issue -> {
                    return issue.getLineNumber() + " " + issue.getCode() + " " + issue.getMessage();
                })
                .toList();

        assertThat(issues).isEmpty();
    }
}
