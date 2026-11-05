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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ReferenceSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void elementReferenceResolvesToDeclaration() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                redefine tran { count := count + 1 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var countVariable =
                parsedPackage.classByName("Counter").variableByName("count").eObject();
        var references = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), ElementReference.class);
        assertThat(references).isNotEmpty();
        assertThat(references.stream()
                        .filter(reference -> reference.getElement() == countVariable)
                        .toList())
                .as("at least one reference resolves to variable count")
                .isNotEmpty();
    }

    @Test
    void selfReference() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                redefine tran { self.count := 1 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var selves = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), SelfReference.class);
        assertThat(selves).hasSize(1);
    }

    @Test
    void navigationSuffixBasic() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker { var done: bool := false }
            class Host {
                contains worker: Worker[1]
                redefine tran { worker.done := true }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var navigations = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), NavigationSuffixExpression.class);
        assertThat(navigations).hasSize(1);
        var navigation = navigations.getFirst();
        assertThat(navigation.isOptional()).isFalse();
        assertThat(navigation.getMember())
                .isSameAs(parsedPackage
                        .classByName("Worker")
                        .variableByName("done")
                        .eObject());
    }

    @Test
    void navigationSuffixOptional() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker { tran step() { } }
            class Host {
                contains worker: Worker[0..1]
                redefine tran { inline worker?.step() }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var navigations = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), NavigationSuffixExpression.class);
        assertThat(navigations).hasSize(1);
        assertThat(navigations.getFirst().isOptional()).isTrue();
    }

    @Test
    void navigationSuffixChained() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf { var value: int := 0 }
            class Middle { contains leaf: Leaf[1] }
            class Host {
                contains middle: Middle[1]
                redefine tran { middle.leaf.value := 5 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var navigations = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Host").anonymousMain().eObject(), NavigationSuffixExpression.class);
        assertThat(navigations).hasSize(2);
    }

    @Test
    void indexingSuffix() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Buffer {
                var values: int[3] := [0, 0, 0]
                redefine tran { values[1] := 5 }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var indexings = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Buffer").anonymousMain().eObject(), IndexingSuffixExpression.class);
        assertThat(indexings).hasSize(1);
        assertThat(indexings.getFirst().getPrimary()).isNotNull();
        assertThat(indexings.getFirst().getIndex()).isNotNull();
    }

    @Test
    void callSuffixWithPositionalArguments() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran addBy(amount: int) { count := count + amount }
                redefine tran { inline addBy(5) }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var calls = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), CallSuffixExpression.class);
        assertThat(calls).hasSize(1);
        var call = calls.getFirst();
        assertThat(call.getArguments()).hasSize(1);
        assertThat(call.getArguments().getFirst().getParameter()).isNull();
    }

    @Test
    void callSuffixWithNamedArgument() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                var count: int := 0
                tran addBy(amount: int) { count := count + amount }
                redefine tran { inline addBy(amount = 5) }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var calls = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), CallSuffixExpression.class);
        assertThat(calls).hasSize(1);
        var argument = calls.getFirst().getArguments().getFirst();
        assertThat(argument.getParameter()).isNotNull();
        assertThat(argument.getParameter().getName()).isEqualTo("amount");
    }

    @Test
    void callSuffixWithNoArguments() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Counter {
                tran step() { }
                redefine tran { inline step() }
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var calls = EcoreUtil2.eAllOfType(
                parsedPackage.classByName("Counter").anonymousMain().eObject(), CallSuffixExpression.class);
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().getArguments()).isEmpty();
    }

    @Test
    void enumLiteralReferenceUsesQualifiedSeparator() {
        var parsedPackage = parseHelper.parse("""
            package test
            enum Color { Red, Green }
            class Lamp { var color: Color := Color::Red }
            """);
        parsedPackage.assertNoResourceErrors();
        var red = parsedPackage.enumByName("Color").literalByName("Red");
        var initializer = (ElementReference)
                parsedPackage.classByName("Lamp").variableByName("color").initializer();
        assertThat(initializer.getElement()).isSameAs(red);
    }
}
