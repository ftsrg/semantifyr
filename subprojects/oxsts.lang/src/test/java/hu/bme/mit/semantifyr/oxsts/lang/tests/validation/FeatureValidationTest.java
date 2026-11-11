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
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class FeatureValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void derivedReferenceWithoutAnOppositeIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Node {
                derived refers neighbours: Node[0..*]
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertErrors(parsedPackage, OxstsValidator.UNSUPPORTED_FEATURE + "@3");
        parsedPackage.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "'neighbours' has none");
    }

    @Test
    void derivedReferenceWithAnOppositePasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Node {
                derived refers incoming: Edge[0..*] opposite target
            }
            class Edge {
                refers target: Node opposite incoming
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void redefinedDerivedReferenceKeepsTheInheritedOpposite() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Node {
                derived refers incoming: Edge[0..*] opposite target
            }
            class Station : Node {
                redefine derived refers incoming: Edge[0..*]
            }
            class Edge {
                refers target: Node opposite incoming
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void subsettingABoundReferenceIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Holder {
                contains first: Item
                contains second: Item
                refers chosen: Item[0..*] = first
                refers extra: Item[0..1] subsets chosen = second
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertErrors(parsedPackage, OxstsValidator.INCORRECT_SUBSETS + "@7");
        parsedPackage.assertHasValidationIssue(OxstsValidator.INCORRECT_SUBSETS, "'extra' cannot subset 'chosen'");
    }

    @Test
    void subsettingADerivedReferenceIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Node {
                derived refers incoming: Edge[0..*] opposite target
                refers preferred: Edge[0..1] subsets incoming
            }
            class Edge {
                refers target: Node opposite incoming
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertErrors(parsedPackage, OxstsValidator.INCORRECT_SUBSETS + "@4");
    }

    @Test
    void subsettingAnUnboundReferencePasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Holder {
                contains first: Item
                refers all: Item[0..*]
                refers chosen: Item[0..1] subsets all = first
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void concreteClassLeavingAnInheritedAbstractFeatureIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            abstract class Container {
                features payload: Item
            }
            class Box : Container { }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertErrors(parsedPackage, OxstsValidator.ABSTRACT_FEATURE_NOT_REALIZED + "@6");
        parsedPackage.assertHasValidationIssue(OxstsValidator.ABSTRACT_FEATURE_NOT_REALIZED, "'payload'");
    }

    @Test
    void concreteClassDeclaringAnAbstractFeatureWithoutSubsettersIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Box {
                features payload: Item
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertErrors(parsedPackage, OxstsValidator.ABSTRACT_FEATURE_NOT_REALIZED + "@3");
    }

    @Test
    void redefiningAnInheritedAbstractFeaturePasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            abstract class Container {
                features payload: Item
            }
            class Box : Container {
                redefine contains payload: Item
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void subsettingAnInheritedAbstractFeaturePasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            abstract class Container {
                features payload: Item[0..*]
            }
            class Box : Container {
                contains first: Item subsets payload
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void abstractClassMayLeaveAnAbstractFeature() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            abstract class Container {
                features payload: Item
            }
            abstract class Crate : Container { }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    private static void assertErrors(WrappedOxstsPackage parsedPackage, String... codesAtLines) {
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> {
                    return issue.getSeverity() == Severity.ERROR;
                })
                .extracting(issue -> {
                    return issue.getCode() + "@" + issue.getLineNumber();
                })
                .containsExactlyInAnyOrder(codesAtLines);
    }
}
