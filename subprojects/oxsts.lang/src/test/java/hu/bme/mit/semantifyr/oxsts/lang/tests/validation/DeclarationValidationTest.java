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
        var pkg = parseHelper.parse("""
            package test
            abstract class Shape {
                abstract tran grow()
                abstract prop area(): int
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationIssues();
    }

    @Test
    void abstractTransitionOfAConcreteClassIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Shape {
                abstract tran grow()
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.MEMBER_NOT_IN_ABSTRACT_CLASS, 3);
    }

    @Test
    void abstractPropertyOfAConcreteClassIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Shape {
                abstract prop area(): int
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.MEMBER_NOT_IN_ABSTRACT_CLASS, 3);
    }

    @Test
    void concreteClassLeavingAnInheritedAbstractMemberIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            abstract class Shape {
                abstract tran grow()
                abstract prop area(): int
            }
            class Square : Shape {
                redefine tran grow() { }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED, "'area'");
        assertOnlyErrorsAre(pkg, OxstsValidator.ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED, 6);
    }

    @Test
    void concreteClassRedefiningEveryAbstractMemberPasses() {
        var pkg = parseHelper.parse("""
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

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationIssues();
    }

    @Test
    void abstractVerificationCaseIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            @VerificationCase
            abstract class Case {
                var x: int := 0
                prop { return AG x == 0 }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.ABSTRACT_VERIFICATION_CASE, 3);
    }

    @Test
    void concreteVerificationCasePasses() {
        var pkg = parseHelper.parse("""
            package test
            @VerificationCase
            class Case {
                var x: int := 0
                prop { return AG x == 0 }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationIssues();
    }

    @Test
    void redefinitionWithoutAnInheritedMemberIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Item { }
            class Base { }
            class Child : Base {
                redefine tran step() { }
                redefine prop ready(): bool { return true }
                redefine contains part: Item
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertThat(pkg.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .extracting(issue -> issue.getCode() + "@" + issue.getLineNumber())
                .containsExactlyInAnyOrder(
                        OxstsValidator.REDEFINED_NOT_FOUND_ISSUE + "@5",
                        OxstsValidator.REDEFINED_NOT_FOUND_ISSUE + "@6",
                        OxstsValidator.REDEFINED_NOT_FOUND_ISSUE + "@7");
    }

    @Test
    void redefinitionOfAnInheritedMemberPasses() {
        var pkg = parseHelper.parse("""
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

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationIssues();
    }

    @Test
    void memberWithAnInheritedNameWarnsWithoutRedefine() {
        var pkg = parseHelper.parse("""
            package test
            class Base {
                tran step() { }
            }
            class Child : Base {
                tran step() { }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
        assertThat(pkg.getIssues()).singleElement().satisfies(issue -> {
            assertThat(issue.getCode()).isEqualTo(OxstsValidator.ELEMENT_SHADOWED);
            assertThat(issue.getSeverity()).isEqualTo(Severity.WARNING);
            assertThat(issue.getLineNumber()).isEqualTo(6);
        });
    }

    @Test
    void duplicateTopLevelNamesAreRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Twin { }
            enum Twin { A }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertThat(pkg.getIssues())
                .filteredOn(issue -> OxstsValidator.DUPLICATE_NAME_ISSUE.equals(issue.getCode()))
                .extracting(issue -> issue.getLineNumber())
                .containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void duplicateClassMembersAreRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran x() { }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertThat(pkg.getIssues())
                .filteredOn(issue -> OxstsValidator.DUPLICATE_NAME_ISSUE.equals(issue.getCode()))
                .extracting(issue -> issue.getLineNumber())
                .containsExactlyInAnyOrder(3, 4);
    }

    @Test
    void duplicateEnumLiteralsAreRejected() {
        var pkg = parseHelper.parse("""
            package test
            enum Color { Red, Green, Red }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.DUPLICATE_NAME_ISSUE, "'Red'");
    }

    @Test
    void duplicateInnerFeaturesAreRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Item { }
            class Inner {
                refers target: Item[0..1]
            }
            class Outer {
                contains a: Item
                contains b: Item
                contains inner: Inner {
                    redefine refers target: Item[0..1] = a
                    redefine refers target: Item[0..1] = b
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.DUPLICATE_NAME_ISSUE, "'target'");
    }

    @Test
    void externDatatypeOutsideTheBuiltinLibraryIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            extern datatype byte
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.DATA_TYPE_NOT_IN_BUILTIN_ISSUE, 2);
    }

    @Test
    void matchingContainmentAndContainerPass() {
        var pkg = parseHelper.parse("""
            package test
            class Parent {
                contains kid: Child[1] opposite parent
            }
            class Child {
                container parent: Parent[1] opposite kid
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationIssues();
    }

    @Test
    void containerWithoutOppositeIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Parent { }
            class Child {
                container parent: Parent[1]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.INCORRECT_OPPOSITE, "must have an opposite");
    }

    @Test
    void oppositeThatDoesNotPointBackIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Parent {
                contains kid: Child[1] opposite parent
                contains other: Child[1]
            }
            class Child {
                container parent: Parent[1] opposite other
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.INCORRECT_OPPOSITE, "Expected feature to have opposite");
    }

    @Test
    void containmentPairedWithAReferenceIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Parent {
                contains kid: Child[1] opposite parent
            }
            class Child {
                refers parent: Parent[1] opposite kid
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.INCORRECT_OPPOSITE, "Containment's opposite must be container");
    }

    @Test
    void variableWithoutATypeIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x := 0
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.VARIABLE_WITH_IMPLICIT_TYPE, 3);
    }

    @Test
    void callingAVariableIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                var y: int := 0
                redefine tran { y := x() }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.INCORRECT_CALLED_ELEMENT, "not callable");
    }

    @Test
    void assigningAFeatureIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                refers limit: int = 3
                redefine tran { limit := 4 }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.INCORRECT_ASSIGNMENT, "Only variables");
    }

    @Test
    void redefinedPropertyMayNarrowTheReturnType() {
        var pkg = parseHelper.parse("""
            package test
            abstract class Base {
                abstract prop value(): any
            }
            class Child : Base {
                redefine prop value(): int { return 1 }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationIssues();
    }

    @Test
    void redefinedPropertyWithAnUnrelatedReturnTypeIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            abstract class Base {
                abstract prop value(): int
            }
            class Child : Base {
                redefine prop value(): bool { return true }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.TYPE_MISMATCH, 6);
    }

    @Test
    void redefinitionMayNarrowParameterTypes() {
        var pkg = parseHelper.parse("""
            package test
            abstract class Attribute {
                abstract tran set(value: any)
            }
            class IntegerAttribute : Attribute {
                var stored: int := 0
                redefine tran set(value: int) { stored := value }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationIssues();
    }

    @Test
    void efBodyMustBeBoolean() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                prop { return EF x }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "`EF` body must be boolean");
    }

    @Test
    void inlineIfGuardMustBeBoolean() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran {
                    inline if (1) { x := 1 }
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "`inline if` guard must be boolean");
    }

    @Test
    void unaryMinusOnABooleanIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                var x: int := 0
                redefine tran { x := -b }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "unary arithmetic operand must be numeric");
    }

    @Test
    void infinityIsNoValue() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := * }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.TYPE_MISMATCH, 4);
    }

    private static void assertOnlyErrorsAre(WrappedOxstsPackage pkg, String code, int line) {
        assertThat(pkg.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(code);
                    assertThat(issue.getLineNumber()).isEqualTo(line);
                });
    }
}
