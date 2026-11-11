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
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.WrappedOxstsPackage;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class SelfReferenceValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void selfInsideAClassPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Node {
                refers me: Node = self
                var count: int := 0
                tran step(amount: int) {
                    self.count := self.count + amount
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void selfInAGlobalFeatureIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Node { }
            global reference root: Node = self
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertSelfOutsideClassAt(parsedPackage, 3);
    }

    @Test
    void selfInANestedBlockOfAGlobalFeatureIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Node {
                refers other: Node[0..1]
            }
            global containment root: Node {
                redefine refers other: Node[0..1] = self
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertSelfOutsideClassAt(parsedPackage, 6);
    }

    @Test
    void selfInARecordFieldIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            record Point {
                var x: int := self
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertSelfOutsideClassAt(parsedPackage, 3);
    }

    private static void assertSelfOutsideClassAt(WrappedOxstsPackage parsedPackage, int line) {
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> {
                    return OxstsValidator.SELF_OUTSIDE_CLASS.equals(issue.getCode());
                })
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getLineNumber()).isEqualTo(line);
                    assertThat(issue.getMessage()).contains("in no class");
                });
    }
}
