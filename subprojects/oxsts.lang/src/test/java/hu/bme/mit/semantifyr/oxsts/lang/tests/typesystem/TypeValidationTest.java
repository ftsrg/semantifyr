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
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran { value := 5 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void assigningBoolToIntIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran { value := true }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "assign");
    }

    @Test
    void assigningIntToBoolIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                redefine tran { flag := 5 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "assign");
    }

    @Test
    void ifGuardInt_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran {
                    if (value) { value := 1 } else { value := 0 }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "guard");
    }

    @Test
    void ifGuardBool_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                var value: int := 0
                redefine tran {
                    if (flag) { value := 1 } else { value := 0 }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void assumeWithIntExpression_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran {
                    assume(value)
                    value := 1
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "assume");
    }

    @Test
    void addingBoolsIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                var flag: bool := false
                redefine tran { value := flag + 1 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "arithmetic");
    }

    @Test
    void andIngIntsIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                var flag: bool := false
                redefine tran { flag := value && 1 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "boolean");
    }

    @Test
    void negatingIntIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                var flag: bool := false
                redefine tran { flag := !value }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "negation");
    }

    @Test
    void comparingIncompatibleTypesIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                var number: int := 0
                var result: bool := false
                redefine tran { result := (flag == number) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "compare");
    }

    private static final String GAMMA_PORTS = """
            package test
            class Event { }
            class Interface {
                contains events: Event[0..*]
                contains inputEvents: Event[0..*] subsets events
                contains outputEvents: Event[0..*] subsets events
            }
            class Port {
                contains interface: Interface[1]
                refers outputEvents: Event[0..*]
                var outputEvent: outputEvents[0..1] := nothing
            }
            class ProvidedPort : Port {
                redefine refers outputEvents: Event[0..*] = interface.outputEvents
            }
            class PingPong : Interface {
                contains ping: Event subsets outputEvents
            }
            class Other { }
            """;

    @Test
    void comparingUnrelatedFeaturesOfTheSameClassPasses() {
        var parsedPackage = parseHelper.parse(GAMMA_PORTS + """
            class Component {
                contains port: ProvidedPort {
                    redefine contains interface: PingPong
                }
                prop { return EF port.outputEvent == port.interface.ping }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void comparingUnrelatedFeaturesOfDifferentClassesIsRejected() {
        var parsedPackage = parseHelper.parse(GAMMA_PORTS + """
            class Component {
                contains port: ProvidedPort {
                    redefine contains interface: PingPong
                }
                contains other: Other
                prop { return EF port.outputEvent == other }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "compare");
    }

    @Test
    void assigningSubclassInstanceToBaseFieldPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Base { }
            class Child : Base { }
            class Host {
                contains children: Child[0..*]
                contains child: Child[1] subsets children
                var selected: children[0..1] := nothing
                redefine tran { selected := child }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void ifThenElseNonBooleanGuard_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var number: int := 0
                redefine tran {
                    var result: int := 0
                    result := if number then 1 else 0
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "guard");
    }

    @Test
    void ifThenElseIncompatibleBranches_areRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                redefine tran {
                    var result: int := 0
                    result := if flag then 1 else true
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "incompatible");
    }

    @Test
    void ifThenElseCompatibleBranchesOfSameType_pass() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                redefine tran {
                    var result: int := 0
                    result := if flag then 1 else 2
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void ifThenElseInPropertyBody_isAllowed() {
        var parsedPackage = parseHelper.parse("""
            package test
            @VerificationCase
            class Holder {
                var number: int := 0
                redefine tran { number := number + 1 }
                prop { return EF (if number > 0 then true else false) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void callArgumentMatchingParameterType_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var total: int := 0
                tran addBy(amount: int) { total := total + amount }
                redefine tran { inline addBy(5) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void callArgumentOfWrongType_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var total: int := 0
                tran addBy(amount: int) { total := total + amount }
                redefine tran { inline addBy(true) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "not assignable to parameter");
    }

    @Test
    void featureTypedVariableArgumentToOptionalClassParameter_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker {
                tran step() { }
            }
            class Holder {
                contains workers: Worker[0..*]
                contains lead: Worker[1] subsets workers
                var current: workers[0..1] := nothing
                tran run(worker: Worker[0..1]) { inline worker.step() }
                redefine tran { inline run(current) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void optionalFeatureTypedVariableArgumentToRequiredParameter_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker {
                tran step() { }
            }
            class Holder {
                contains workers: Worker[0..*]
                contains lead: Worker[1] subsets workers
                var current: workers[0..1] := nothing
                tran run(worker: Worker) { inline worker.step() }
                redefine tran { inline run(current) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "not assignable to parameter 'worker'");
    }

    @Test
    void annotationArgumentOfWrongType_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Tag(priority: int)
            @Tag(true)
            class Holder { }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "not assignable to parameter");
    }

    @Test
    void annotationArgumentMatchingParameter_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Tag(priority: int)
            @Tag(42)
            class Holder { }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void propertyReturningWrongType_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var number: int := 0
                redefine tran { number := 1 }
                prop signed(): int { return true }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "expected to return");
    }

    @Test
    void propertyReturningDeclaredType_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var number: int := 0
                redefine tran { number := 1 }
                prop signed(): int { return number + 1 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void propertyDefaultBoolReturn_enforcesBool() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var number: int := 0
                redefine tran { number := 1 }
                prop { return number }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "expected to return");
    }

    @Test
    void variableInitializerOfWrongType_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := true
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "Initializer of type");
    }

    @Test
    void variableInitializerMatchingDeclaredType_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 5
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void featureBoundExpressionOfWrongType_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                refers size: int = true
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "Feature bound expression");
    }

    @Test
    void havocOnVariable_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran { havoc(value) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void havocOnFeature_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                refers size: int = 3
                redefine tran { havoc(size) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INCORRECT_ASSIGNMENT, "variable reference");
    }

    @Test
    void forOverIntRange_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var total: int := 0
                redefine tran { inline for (index in 1..5) { total := total + index } }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void forOverScalarInt_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var total: int := 0
                redefine tran { inline for (index in 5) { total := total + 1 } }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "`for` range");
    }

    @Test
    void redefinedFeatureWithCompatibleType_passes() {
        var parsedPackage = parseHelper.parse("""
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

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void redefinedFeatureWithIncompatibleType_isRejected() {
        var parsedPackage = parseHelper.parse("""
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

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "Redefinition has type");
    }

    @Test
    void agBodyMustBeBoolean() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran { value := 1 }
                prop { return AG value }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "AG");
    }
}
