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
public class RedefinitionSignatureTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void transitionRedefinitionWithSameSignaturePasses() {
        var pkg = parseHelper.parse("""
            package test
            class Node { }
            abstract class Base {
                abstract tran enter(from: Node)
            }
            class Child : Base {
                redefine tran enter(from: Node) { }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationIssues();
    }

    @Test
    void transitionRedefinitionWithFewerParametersIsFlagged() {
        var pkg = parseHelper.parse("""
            package test
            class Node { }
            abstract class Base {
                abstract tran enter(from: Node)
            }
            class Child : Base {
                redefine tran enter() { }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.REDEFINITION_SIGNATURE_MISMATCH, "1 parameter");
    }

    @Test
    void propertyRedefinitionWithMoreParametersIsFlagged() {
        var pkg = parseHelper.parse("""
            package test
            abstract class Base {
                abstract prop check(): bool
            }
            class Child : Base {
                redefine prop check(limit: int): bool { return limit > 0 }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.REDEFINITION_SIGNATURE_MISMATCH, "0 parameters");
    }

    @Test
    void signatureMismatchIsAnError() {
        var pkg = parseHelper.parse("""
            package test
            class Node { }
            abstract class Base {
                abstract tran enter(from: Node)
            }
            class Child : Base {
                redefine tran enter() { }
            }
            """, CheckMode.ALL);

        assertThat(pkg.getIssues())
                .filteredOn(issue -> OxstsValidator.REDEFINITION_SIGNATURE_MISMATCH.equals(issue.getCode()))
                .extracting(issue -> issue.getSeverity())
                .containsOnly(Severity.ERROR);
    }
}
