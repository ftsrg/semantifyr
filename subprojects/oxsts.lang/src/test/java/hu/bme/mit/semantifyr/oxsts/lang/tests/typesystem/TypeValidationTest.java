/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.typesystem;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class TypeValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void assigningIntToIntPasses() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := 5 }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void assigningBoolToIntIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := true }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "assign");
    }

    @Test
    void assigningIntToBoolIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                redefine tran { b := 5 }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "assign");
    }

    @Test
    void ifGuardInt_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran {
                    if (x) { x := 1 } else { x := 0 }
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "guard");
    }

    @Test
    void ifGuardBool_passes() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                var x: int := 0
                redefine tran {
                    if (b) { x := 1 } else { x := 0 }
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void assumeWithIntExpression_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran {
                    assume(x)
                    x := 1
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "assume");
    }

    @Test
    void addingBoolsIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                var b: bool := false
                redefine tran { x := b + 1 }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "arithmetic");
    }

    @Test
    void andIngIntsIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                var b: bool := false
                redefine tran { b := x && 1 }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "boolean");
    }

    @Test
    void negatingIntIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                var b: bool := false
                redefine tran { b := !x }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "negation");
    }

    @Test
    void comparingIncompatibleTypesIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                var n: int := 0
                var result: bool := false
                redefine tran { result := (b == n) }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "compare");
    }

    @Test
    void assigningSubclassInstanceToBaseFieldPasses() {
        var pkg = parseHelper.parse("""
            package test
            class Base { }
            class Child : Base { }
            class Host {
                contains children: Child[0..*]
                contains child: Child[1] subsets children
                var ref: children[0..1] := nothing
                redefine tran { ref := child }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void ifThenElseNonBooleanGuard_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var n: int := 0
                redefine tran {
                    var result: int := 0
                    result := if n then 1 else 0
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "guard");
    }

    @Test
    void ifThenElseIncompatibleBranches_areRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                redefine tran {
                    var result: int := 0
                    result := if b then 1 else true
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "incompatible");
    }

    @Test
    void ifThenElseCompatibleBranchesOfSameType_pass() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                redefine tran {
                    var result: int := 0
                    result := if b then 1 else 2
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void ifThenElseInPropertyBody_isAllowed() {
        var pkg = parseHelper.parse("""
            package test
            @VerificationCase
            class C {
                var n: int := 0
                redefine tran { n := n + 1 }
                prop { return EF (if n > 0 then true else false) }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void callArgumentMatchingParameterType_passes() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                tran addBy(n: int) { total := total + n }
                redefine tran { inline addBy(5) }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void callArgumentOfWrongType_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                tran addBy(n: int) { total := total + n }
                redefine tran { inline addBy(true) }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "not assignable to parameter");
    }

    @Test
    void annotationArgumentOfWrongType_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            annotation Tag(n: int)
            @Tag(true)
            class C { }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "not assignable to parameter");
    }

    @Test
    void annotationArgumentMatchingParameter_passes() {
        var pkg = parseHelper.parse("""
            package test
            annotation Tag(n: int)
            @Tag(42)
            class C { }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void propertyReturningWrongType_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var n: int := 0
                redefine tran { n := 1 }
                prop signed(): int { return true }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "expected to return");
    }

    @Test
    void propertyReturningDeclaredType_passes() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var n: int := 0
                redefine tran { n := 1 }
                prop signed(): int { return n + 1 }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void propertyDefaultBoolReturn_enforcesBool() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var n: int := 0
                redefine tran { n := 1 }
                prop { return n }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "expected to return");
    }

    @Test
    void variableInitializerOfWrongType_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := true
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "Initializer of type");
    }

    @Test
    void variableInitializerMatchingDeclaredType_passes() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 5
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void featureBoundExpressionOfWrongType_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                refers size: int = true
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "Feature bound expression");
    }

    @Test
    void havocOnVariable_passes() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { havoc(x) }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void havocOnFeature_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                refers size: int = 3
                redefine tran { havoc(size) }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INCORRECT_ASSIGNMENT, "variable reference");
    }

    @Test
    void forOverIntRange_passes() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                redefine tran { inline for (n in 1..5) { total := total + n } }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void forOverScalarInt_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                redefine tran { inline for (n in 5) { total := total + 1 } }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "`for` range");
    }

    @Test
    void redefinedFeatureWithCompatibleType_passes() {
        var pkg = parseHelper.parse("""
            package test
            class Leaf { }
            class SpecialLeaf : Leaf { }
            class Base {
                contains leaf: Leaf[1]
            }
            class Child : Base {
                redefine contains leaf: SpecialLeaf[1]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void redefinedFeatureWithIncompatibleType_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Leaf { }
            class Other { }
            class Base {
                contains leaf: Leaf[1]
            }
            class Child : Base {
                redefine contains leaf: Other[1]
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "Redefinition has type");
    }

    @Test
    void agBodyMustBeBoolean() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := 1 }
                prop { return AG x }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "AG");
    }
}
