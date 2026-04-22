/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.syntax;

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

import static org.assertj.core.api.Assertions.assertThat;

@InjectWithOxsts
public class ReferenceSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void elementReferenceResolvesToDeclaration() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := x + 1 }
            }
            """);
        pkg.assertNoResourceErrors();
        var xVar = pkg.classByName("C").variableByName("x").eObject();
        var refs = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), ElementReference.class);
        assertThat(refs).isNotEmpty();
        assertThat(refs.stream().filter(r -> r.getElement() == xVar).toList())
            .as("at least one reference resolves to variable x")
            .isNotEmpty();
    }

    @Test
    void selfReference() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { self.x := 1 }
            }
            """);
        pkg.assertNoResourceErrors();
        var selves = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), SelfReference.class);
        assertThat(selves).hasSize(1);
    }

    @Test
    void navigationSuffixBasic() {
        var pkg = parseHelper.parse("""
            package test
            class Worker { var done: bool := false }
            class Host {
                contains w: Worker[1]
                redefine tran { w.done := true }
            }
            """);
        pkg.assertNoResourceErrors();
        var navs = EcoreUtil2.eAllOfType(pkg.classByName("Host").anonymousMain().eObject(), NavigationSuffixExpression.class);
        assertThat(navs).hasSize(1);
        var nav = navs.getFirst();
        assertThat(nav.isOptional()).isFalse();
        assertThat(nav.getMember()).isSameAs(pkg.classByName("Worker").variableByName("done").eObject());
    }

    @Test
    void navigationSuffixOptional() {
        var pkg = parseHelper.parse("""
            package test
            class Worker { tran step() { } }
            class Host {
                contains w: Worker[0..1]
                redefine tran { inline w?.step() }
            }
            """);
        pkg.assertNoResourceErrors();
        var navs = EcoreUtil2.eAllOfType(pkg.classByName("Host").anonymousMain().eObject(), NavigationSuffixExpression.class);
        assertThat(navs).hasSize(1);
        assertThat(navs.getFirst().isOptional()).isTrue();
    }

    @Test
    void navigationSuffixChained() {
        var pkg = parseHelper.parse("""
            package test
            class Leaf { var v: int := 0 }
            class Mid { contains leaf: Leaf[1] }
            class Host {
                contains mid: Mid[1]
                redefine tran { mid.leaf.v := 5 }
            }
            """);
        pkg.assertNoResourceErrors();
        var navs = EcoreUtil2.eAllOfType(pkg.classByName("Host").anonymousMain().eObject(), NavigationSuffixExpression.class);
        assertThat(navs).hasSize(2);
    }

    @Test
    void indexingSuffix() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[3] := [0, 0, 0]
                redefine tran { values[1] := 5 }
            }
            """);
        pkg.assertNoResourceErrors();
        var idxs = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), IndexingSuffixExpression.class);
        assertThat(idxs).hasSize(1);
        assertThat(idxs.getFirst().getPrimary()).isNotNull();
        assertThat(idxs.getFirst().getIndex()).isNotNull();
    }

    @Test
    void callSuffixWithPositionalArguments() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addBy(n: int) { x := x + n }
                redefine tran { inline addBy(5) }
            }
            """);
        pkg.assertNoResourceErrors();
        var calls = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), CallSuffixExpression.class);
        assertThat(calls).hasSize(1);
        var call = calls.getFirst();
        assertThat(call.getArguments()).hasSize(1);
        assertThat(call.getArguments().getFirst().getParameter()).isNull();
    }

    @Test
    void callSuffixWithNamedArgument() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran addBy(n: int) { x := x + n }
                redefine tran { inline addBy(n = 5) }
            }
            """);
        pkg.assertNoResourceErrors();
        var calls = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), CallSuffixExpression.class);
        assertThat(calls).hasSize(1);
        var arg = calls.getFirst().getArguments().getFirst();
        assertThat(arg.getParameter()).isNotNull();
        assertThat(arg.getParameter().getName()).isEqualTo("n");
    }

    @Test
    void callSuffixWithNoArguments() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                tran step() { }
                redefine tran { inline step() }
            }
            """);
        pkg.assertNoResourceErrors();
        var calls = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), CallSuffixExpression.class);
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().getArguments()).isEmpty();
    }

    @Test
    void enumLiteralReferenceUsesQualifiedSeparator() {
        var pkg = parseHelper.parse("""
            package test
            enum Color { Red, Green }
            class C { var c: Color := Color::Red }
            """);
        pkg.assertNoResourceErrors();
        var red = pkg.enumByName("Color").literalByName("Red");
        var init = (ElementReference) pkg.classByName("C").variableByName("c").initializer();
        assertThat(init.getElement()).isSameAs(red);
    }
}
