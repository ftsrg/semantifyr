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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind;
import org.eclipse.xtext.resource.XtextSyntaxDiagnostic;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ClassMemberSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void variableWithTypeAndInitializer() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var counter: int := 5 }
            """);
        parsedPackage.assertNoResourceErrors();
        var variable = parsedPackage.classByName("Host").variableByName("counter");
        assertThat(variable.typeSpecification()).isNotNull();
        assertThat(variable.initializer()).isNotNull();
    }

    @Test
    void variableWithTypeOnly() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var counter: int }
            """);
        parsedPackage.assertNoResourceErrors();
        var variable = parsedPackage.classByName("Host").variableByName("counter");
        assertThat(variable.typeSpecification()).isNotNull();
        assertThat(variable.initializer()).isNull();
    }

    @Test
    void variableWithInitializerOnly() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host { var counter := 5 }
            """);
        parsedPackage.assertNoResourceErrors();
        var variable = parsedPackage.classByName("Host").variableByName("counter");
        assertThat(variable.typeSpecification()).isNull();
        assertThat(variable.initializer()).isNotNull();
    }

    @Test
    void redefinedVariableIsAParseError() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Base { var counter: int := 0 }
            class Child : Base {
                redefine var counter: int := 1
            }
            """);
        assertThat(parsedPackage.getResourceErrors())
                .anySatisfy(error -> assertThat(error).isInstanceOf(XtextSyntaxDiagnostic.class));
    }

    @Test
    void anonymousMainTransition() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var counter: int := 0
                redefine tran { counter := 1 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var main = parsedPackage.classByName("Host").anonymousMain();
        assertThat(main.kind()).isEqualTo(TransitionKind.TRAN);
        assertThat(main.name()).isNull();
        assertThat(main.isRedefine()).isTrue();
    }

    @Test
    void anonymousInitTransition() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var counter: int := 0
                redefine init { counter := 1 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.classByName("Host").anonymousInit().kind()).isEqualTo(TransitionKind.INIT);
    }

    @Test
    void namedTransitionWithoutParameters() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var counter: int := 0
                tran step() { counter := counter + 1 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var step = parsedPackage.classByName("Host").namedTransition("step");
        assertThat(step.parameters()).isEmpty();
        assertThat(step.isAbstract()).isFalse();
        assertThat(step.isRedefine()).isFalse();
    }

    @Test
    void namedParametricTransition() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var counter: int := 0
                tran addBy(amount: int) { counter := counter + amount }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var addBy = parsedPackage.classByName("Host").namedTransition("addBy");
        assertThat(addBy.parameters()).hasSize(1);
        assertThat(addBy.parameters().getFirst().name()).isEqualTo("amount");
        assertThat(addBy.parameters().getFirst().typeDomain()).isNotNull();
    }

    @Test
    void transitionWithMultipleParameters() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var counter: int := 0
                tran setBy(first: int, second: int) { counter := first + second }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.classByName("Host").namedTransition("setBy").parameters())
                .hasSize(2);
    }

    @Test
    void abstractTransition() {
        var parsedPackage = parseHelper.parse("""
            package test
            abstract class Base {
                abstract tran doIt()
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var doIt = parsedPackage.classByName("Base").namedTransition("doIt");
        assertThat(doIt.isAbstract()).isTrue();
        assertThat(doIt.branches()).isEmpty();
    }

    @Test
    void redefinedTransition() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Base { tran act() { } }
            class Child : Base {
                redefine tran act() { }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.classByName("Child").namedTransition("act").isRedefine())
                .isTrue();
    }

    @Test
    void anonymousProperty() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var counter: int := 0
                redefine tran { counter := counter + 1 }
                prop { return AG (counter >= 0) }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var property = parsedPackage.classByName("Host").anonymousProperty();
        assertThat(property.name()).isNull();
        assertThat(property.expression()).isNotNull();
    }

    @Test
    void namedParametricProperty() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var counter: int := 0
                prop equals(value: int): bool { return counter == value }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var equals = parsedPackage.classByName("Host").namedProperty("equals");
        assertThat(equals.parameters()).hasSize(1);
        assertThat(equals.returnTypeSpecification()).isNotNull();
        assertThat(equals.returnTypeDomain()).isNotNull();
    }

    @Test
    void abstractProperty() {
        var parsedPackage = parseHelper.parse("""
            package test
            abstract class Base {
                abstract prop marker(): bool
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var marker = parsedPackage.classByName("Base").namedProperty("marker");
        assertThat(marker.isAbstract()).isTrue();
        assertThat(marker.expression()).isNull();
    }

    @Test
    void redefinedProperty() {
        var parsedPackage = parseHelper.parse("""
            package test
            abstract class Base {
                abstract prop marker(): bool
            }
            class Child : Base {
                redefine prop marker(): bool { return true }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var marker = parsedPackage.classByName("Child").namedProperty("marker");
        assertThat(marker.isRedefine()).isTrue();
        assertThat(marker.expression()).isNotNull();
    }

    @Test
    void classWithMultipleMemberKinds() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var counter: int := 0
                contains other: Host[0..1]
                tran step() { counter := counter + 1 }
                prop positive(): bool { return counter > 0 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var host = parsedPackage.classByName("Host");
        assertThat(host.variables()).hasSize(1);
        assertThat(host.features()).hasSize(1);
        assertThat(host.transitions()).hasSize(1);
        assertThat(host.properties()).hasSize(1);
    }
}
