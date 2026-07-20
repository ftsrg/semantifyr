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
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ClassMemberSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void variableWithTypeAndInitializer() {
        var pkg = parseHelper.parse("""
            package test
            class C { var x: int := 5 }
            """);
        pkg.assertNoResourceErrors();
        var variable = pkg.classByName("C").variableByName("x");
        assertThat(variable.typeSpecification()).isNotNull();
        assertThat(variable.initializer()).isNotNull();
    }

    @Test
    void variableWithTypeOnly() {
        var pkg = parseHelper.parse("""
            package test
            class C { var x: int }
            """);
        pkg.assertNoResourceErrors();
        var variable = pkg.classByName("C").variableByName("x");
        assertThat(variable.typeSpecification()).isNotNull();
        assertThat(variable.initializer()).isNull();
    }

    @Test
    void variableWithInitializerOnly() {
        var pkg = parseHelper.parse("""
            package test
            class C { var x := 5 }
            """);
        pkg.assertNoResourceErrors();
        var variable = pkg.classByName("C").variableByName("x");
        assertThat(variable.typeSpecification()).isNull();
        assertThat(variable.initializer()).isNotNull();
    }

    // --- transitions ---

    @Test
    void anonymousMainTransition() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := 1 }
            }
            """);
        pkg.assertNoResourceErrors();
        var main = pkg.classByName("C").anonymousMain();
        assertThat(main.kind()).isEqualTo(TransitionKind.TRAN);
        assertThat(main.name()).isNull();
        assertThat(main.isRedefine()).isTrue();
    }

    @Test
    void anonymousInitTransition() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine init { x := 1 }
            }
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.classByName("C").anonymousInit().kind()).isEqualTo(TransitionKind.INIT);
    }

    @Test
    void namedTransitionWithoutParameters() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran step() { x := x + 1 }
            }
            """);
        pkg.assertNoResourceErrors();
        var step = pkg.classByName("C").namedTransition("step");
        assertThat(step.parameters()).isEmpty();
        assertThat(step.isAbstract()).isFalse();
        assertThat(step.isRedefine()).isFalse();
    }

    @Test
    void namedParametricTransition() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addBy(n: int) { x := x + n }
            }
            """);
        pkg.assertNoResourceErrors();
        var addBy = pkg.classByName("C").namedTransition("addBy");
        assertThat(addBy.parameters()).hasSize(1);
        assertThat(addBy.parameters().getFirst().name()).isEqualTo("n");
        assertThat(addBy.parameters().getFirst().typeDomain()).isNotNull();
    }

    @Test
    void transitionWithMultipleParameters() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran setBy(a: int, b: int) { x := a + b }
            }
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.classByName("C").namedTransition("setBy").parameters()).hasSize(2);
    }

    @Test
    void abstractTransition() {
        var pkg = parseHelper.parse("""
            package test
            abstract class Base {
                abstract tran doIt()
            }
            """);
        pkg.assertNoResourceErrors();
        var doIt = pkg.classByName("Base").namedTransition("doIt");
        assertThat(doIt.isAbstract()).isTrue();
        assertThat(doIt.branches()).isEmpty();
    }

    @Test
    void redefinedTransition() {
        var pkg = parseHelper.parse("""
            package test
            class Base { tran act() { } }
            class Child : Base {
                redefine tran act() { }
            }
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.classByName("Child").namedTransition("act").isRedefine()).isTrue();
    }

    @Test
    void anonymousProperty() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := x + 1 }
                prop { return AG (x >= 0) }
            }
            """);
        pkg.assertNoResourceErrors();
        var prop = pkg.classByName("C").anonymousProperty();
        assertThat(prop.name()).isNull();
        assertThat(prop.expression()).isNotNull();
    }

    @Test
    void namedParametricProperty() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                prop equals(n: int): bool { return x == n }
            }
            """);
        pkg.assertNoResourceErrors();
        var equals = pkg.classByName("C").namedProperty("equals");
        assertThat(equals.parameters()).hasSize(1);
        assertThat(equals.returnTypeSpecification()).isNotNull();
        assertThat(equals.returnTypeDomain()).isNotNull();
    }

    @Test
    void abstractProperty() {
        var pkg = parseHelper.parse("""
            package test
            abstract class Base {
                abstract prop marker(): bool
            }
            """);
        pkg.assertNoResourceErrors();
        var marker = pkg.classByName("Base").namedProperty("marker");
        assertThat(marker.isAbstract()).isTrue();
        assertThat(marker.expression()).isNull();
    }

    @Test
    void redefinedProperty() {
        var pkg = parseHelper.parse("""
            package test
            abstract class Base {
                abstract prop marker(): bool
            }
            class Child : Base {
                redefine prop marker(): bool { return true }
            }
            """);
        pkg.assertNoResourceErrors();
        var marker = pkg.classByName("Child").namedProperty("marker");
        assertThat(marker.isRedefine()).isTrue();
        assertThat(marker.expression()).isNotNull();
    }

    @Test
    void classWithMultipleMemberKinds() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                contains other: C[0..1]
                tran step() { x := x + 1 }
                prop positive(): bool { return x > 0 }
            }
            """);
        pkg.assertNoResourceErrors();
        var c = pkg.classByName("C");
        assertThat(c.variables()).hasSize(1);
        assertThat(c.features()).hasSize(1);
        assertThat(c.transitions()).hasSize(1);
        assertThat(c.properties()).hasSize(1);
    }
}
