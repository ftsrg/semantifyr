/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.syntax;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class OperationSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void assignmentOperation() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran { value := 5 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var operations = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Holder").anonymousMain().eObject(), AssignmentOperation.class);
        assertThat(operations).hasSize(1);
        assertThat(operations.getFirst().getReference()).isNotNull();
        assertThat(operations.getFirst().getExpression()).isNotNull();
    }

    @Test
    void assumptionOperation() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran { assume(value > 0); value := 1 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var operations = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Holder").anonymousMain().eObject(), AssumptionOperation.class);
        assertThat(operations).hasSize(1);
        assertThat(operations.getFirst().getExpression()).isNotNull();
    }

    @Test
    void havocOperation() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                redefine tran { havoc(flag) }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var operations = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Holder").anonymousMain().eObject(), HavocOperation.class);
        assertThat(operations).hasSize(1);
        assertThat(operations.getFirst().getReference()).isNotNull();
    }

    @Test
    void choiceOperationWithTwoBranches() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran { choice { value := 1 } or { value := 2 } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var choices = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Holder").anonymousMain().eObject(), ChoiceOperation.class);
        assertThat(choices).hasSize(1);
        assertThat(choices.getFirst().getBranches()).hasSize(2);
    }

    @Test
    void choiceOperationWithManyBranches() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran {
                    choice { value := 1 } or { value := 2 } or { value := 3 } or { value := 4 }
                }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var choices = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Holder").anonymousMain().eObject(), ChoiceOperation.class);
        assertThat(choices).hasSize(1);
        assertThat(choices.getFirst().getBranches()).hasSize(4);
    }

    @Test
    void sequenceOperationFromBlock() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                var other: int := 0
                redefine tran { value := 1; other := 2 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var sequences = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Holder").anonymousMain().eObject(), SequenceOperation.class);
        assertThat(sequences).isNotEmpty();
        var outer = parsedPackage
                .classByName("Holder")
                .anonymousMain()
                .eObject()
                .getBranches()
                .getFirst();
        assertThat(outer.getSteps()).hasSize(2);
    }

    @Test
    void ifOperationWithElse() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                var value: int := 0
                redefine tran { if (flag) { value := 1 } else { value := 0 } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var ifOperations = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Holder").anonymousMain().eObject(), IfOperation.class);
        assertThat(ifOperations).hasSize(1);
        assertThat(ifOperations.getFirst().getGuard()).isNotNull();
        assertThat(ifOperations.getFirst().getBody()).isNotNull();
        assertThat(ifOperations.getFirst().getElse()).isNotNull();
    }

    @Test
    void ifOperationWithoutElse() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                var value: int := 0
                redefine tran { if (flag) { value := 1 } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var ifOperations = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Holder").anonymousMain().eObject(), IfOperation.class);
        assertThat(ifOperations).hasSize(1);
        assertThat(ifOperations.getFirst().getElse()).isNull();
    }

    @Test
    void runtimeForOverIntegerRange() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var total: int := 0
                redefine tran { for (index in 1..3) { total := total + 1 } }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var forOperations = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Holder").anonymousMain().eObject(), ForOperation.class);
        assertThat(forOperations).hasSize(1);
        var forOperation = forOperations.getFirst();
        assertThat(forOperation.getLoopVariable()).isNotNull();
        assertThat(forOperation.getLoopVariable().getName()).isEqualTo("index");
        assertThat(forOperation.getRangeExpression()).isNotNull();
        assertThat(forOperation.getBody()).isNotNull();
    }

    @Test
    void localVariableDeclarationWithInitializer() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran {
                    var snapshot: int := 5
                    value := snapshot
                }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var locals = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Holder").anonymousMain().eObject(), LocalVarDeclarationOperation.class);
        assertThat(locals).hasSize(1);
        assertThat(locals.getFirst().getName()).isEqualTo("snapshot");
        assertThat(locals.getFirst().getTypeSpecification()).isNotNull();
        assertThat(locals.getFirst().getExpression()).isNotNull();
    }

    @Test
    void localVariableDeclarationWithTypeOnly() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran {
                    var snapshot: int
                    havoc(snapshot)
                    value := snapshot
                }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var locals = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Holder").anonymousMain().eObject(), LocalVarDeclarationOperation.class);
        assertThat(locals).hasSize(1);
        assertThat(locals.getFirst().getExpression()).isNull();
    }

    @Test
    void transitionWithMultipleOrBranches() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran { value := 1 } or { value := 2 } or { value := 3 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var mainTransition = parsedPackage.classByName("Holder").anonymousMain().eObject();
        assertThat(mainTransition.getBranches()).hasSize(3);
    }
}
