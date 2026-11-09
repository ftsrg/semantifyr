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
public class DeclarationValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void abstractMembersOfAnAbstractClassPass() {
        var parsedPackage = parseHelper.parse("""
            package test
            abstract class Shape {
                abstract tran grow()
                abstract prop area(): int
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void abstractTransitionOfAConcreteClassIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Shape {
                abstract tran grow()
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.MEMBER_NOT_IN_ABSTRACT_CLASS, 3);
    }

    @Test
    void abstractPropertyOfAConcreteClassIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Shape {
                abstract prop area(): int
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.MEMBER_NOT_IN_ABSTRACT_CLASS, 3);
    }

    @Test
    void concreteClassLeavingAnInheritedAbstractMemberIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            abstract class Shape {
                abstract tran grow()
                abstract prop area(): int
            }
            class Square : Shape {
                redefine tran grow() { }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED, "'area'");
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED, 6);
    }

    @Test
    void concreteClassRedefiningEveryAbstractMemberPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            abstract class Shape {
                abstract tran grow()
                abstract prop area(): int
            }
            abstract class Polygon : Shape {
                redefine tran grow() { }
            }
            class Square : Polygon {
                redefine prop area(): int { return 4 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void abstractVerificationCaseIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            @VerificationCase
            abstract class Case {
                var count: int := 0
                prop { return AG count == 0 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.ABSTRACT_VERIFICATION_CASE, 3);
    }

    @Test
    void concreteVerificationCasePasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            @VerificationCase
            class Case {
                var count: int := 0
                prop { return AG count == 0 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void redefinitionWithoutAnInheritedMemberIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Base { }
            class Child : Base {
                redefine tran step() { }
                redefine prop ready(): bool { return true }
                redefine contains part: Item
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .extracting(issue -> issue.getCode() + "@" + issue.getLineNumber())
                .containsExactlyInAnyOrder(
                        OxstsValidator.REDEFINED_NOT_FOUND_ISSUE + "@5",
                        OxstsValidator.REDEFINED_NOT_FOUND_ISSUE + "@6",
                        OxstsValidator.REDEFINED_NOT_FOUND_ISSUE + "@7");
    }

    @Test
    void redefinitionOfAnInheritedMemberPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Base {
                tran step() { }
                prop ready(): bool { return false }
                contains part: Item
            }
            class Child : Base {
                redefine tran step() { }
                redefine prop ready(): bool { return true }
                redefine contains part: Item
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void memberWithAnInheritedNameWarnsWithoutRedefine() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Base {
                tran step() { }
            }
            class Child : Base {
                tran step() { }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
        assertThat(parsedPackage.getIssues()).singleElement().satisfies(issue -> {
            assertThat(issue.getCode()).isEqualTo(OxstsValidator.ELEMENT_SHADOWED);
            assertThat(issue.getSeverity()).isEqualTo(Severity.WARNING);
            assertThat(issue.getLineNumber()).isEqualTo(6);
        });
    }

    @Test
    void anonymousTransitionsWithoutRedefinePass() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Base {
                var count: int := 0
                init {
                    count := 1
                }
                tran {
                    count := 2
                }
            }
            class Child : Base {
                init {
                    count := 3
                }
                tran {
                    count := 4
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void twoAnonymousTransitionsOfOneKindAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Machine {
                var count: int := 0
                tran {
                    count := 1
                }
                init {
                    count := 0
                }
                tran {
                    count := 2
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .extracting(issue -> issue.getCode() + "@" + issue.getLineNumber())
                .containsExactlyInAnyOrder(
                        OxstsValidator.DUPLICATE_NAME_ISSUE + "@4", OxstsValidator.DUPLICATE_NAME_ISSUE + "@10");
    }

    @Test
    void duplicateTopLevelNamesAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Twin { }
            enum Twin { Single }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> OxstsValidator.DUPLICATE_NAME_ISSUE.equals(issue.getCode()))
                .extracting(issue -> issue.getLineNumber())
                .containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void duplicateClassMembersAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var step: int := 0
                tran step() { }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> OxstsValidator.DUPLICATE_NAME_ISSUE.equals(issue.getCode()))
                .extracting(issue -> issue.getLineNumber())
                .containsExactlyInAnyOrder(3, 4);
    }

    @Test
    void duplicateEnumLiteralsAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            enum Color { Red, Green, Red }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.DUPLICATE_NAME_ISSUE, "'Red'");
    }

    @Test
    void duplicateInnerFeaturesAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Inner {
                refers target: Item[0..1]
            }
            class Outer {
                contains first: Item
                contains second: Item
                contains inner: Inner {
                    redefine refers target: Item[0..1] = first
                    redefine refers target: Item[0..1] = second
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.DUPLICATE_NAME_ISSUE, "'target'");
    }

    @Test
    void externDatatypeOutsideTheBuiltinLibraryIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            extern datatype byte
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.DATA_TYPE_NOT_IN_BUILTIN_ISSUE, 2);
    }

    @Test
    void matchingContainmentAndContainerPass() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Parent {
                contains kid: Child[1] opposite parent
            }
            class Child {
                container parent: Parent[1] opposite kid
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void containerWithoutOppositeIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Parent { }
            class Child {
                container parent: Parent[1]
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.INCORRECT_OPPOSITE, "must have an opposite");
    }

    @Test
    void oppositeThatDoesNotPointBackIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Parent {
                contains kid: Child[1] opposite parent
                contains other: Child[1]
            }
            class Child {
                container parent: Parent[1] opposite other
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.INCORRECT_OPPOSITE, "Expected feature to have opposite");
    }

    @Test
    void containmentPairedWithAReferenceIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Parent {
                contains kid: Child[1] opposite parent
            }
            class Child {
                refers parent: Parent[1] opposite kid
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(
                OxstsValidator.INCORRECT_OPPOSITE, "Containment's opposite must be container");
    }

    @Test
    void variableWithoutATypeIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var count := 0
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.VARIABLE_WITH_IMPLICIT_TYPE, 3);
    }

    @Test
    void callingAVariableIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var count: int := 0
                var result: int := 0
                redefine tran { result := count() }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.INCORRECT_CALLED_ELEMENT, "not callable");
    }

    @Test
    void assigningAFeatureIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                refers limit: int = 3
                redefine tran { limit := 4 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.INCORRECT_ASSIGNMENT, "Only variables");
    }

    @Test
    void redefinedPropertyMayNarrowTheReturnType() {
        var parsedPackage = parseHelper.parse("""
            package test
            abstract class Base {
                abstract prop value(): any
            }
            class Child : Base {
                redefine prop value(): int { return 1 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void redefinedPropertyWithAnUnrelatedReturnTypeIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            abstract class Base {
                abstract prop value(): int
            }
            class Child : Base {
                redefine prop value(): bool { return true }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.TYPE_MISMATCH, 6);
    }

    @Test
    void redefinitionMayNarrowParameterTypes() {
        var parsedPackage = parseHelper.parse("""
            package test
            abstract class Attribute {
                abstract tran set(value: any)
            }
            class IntegerAttribute : Attribute {
                var stored: int := 0
                redefine tran set(value: int) { stored := value }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void efBodyMustBeBoolean() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var count: int := 0
                prop { return EF count }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "`EF` body must be boolean");
    }

    @Test
    void inlineIfGuardMustBeBoolean() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var count: int := 0
                redefine tran {
                    inline if (1) { count := 1 }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "`inline if` guard must be boolean");
    }

    @Test
    void unaryMinusOnABooleanIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                var count: int := 0
                redefine tran { count := -flag }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(
                OxstsValidator.TYPE_MISMATCH, "unary arithmetic operand must be numeric");
    }

    @Test
    void infinityIsNoValue() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var count: int := 0
                redefine tran { count := * }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.TYPE_MISMATCH, 4);
    }

    private static void assertOnlyErrorsAre(WrappedOxstsPackage parsedPackage, String code, int line) {
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(code);
                    assertThat(issue.getLineNumber()).isEqualTo(line);
                });
    }
}
