/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.syntax;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInfinity;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@InjectWithOxsts
public class LiteralSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void booleanTrueLiteral() {
        var pkg = parseHelper.parse("""
            package test
            class C { var b: bool := true }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralBoolean) pkg.classByName("C").variableByName("b").initializer();
        assertThat(init.isValue()).isTrue();
    }

    @Test
    void booleanFalseLiteral() {
        var pkg = parseHelper.parse("""
            package test
            class C { var b: bool := false }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralBoolean) pkg.classByName("C").variableByName("b").initializer();
        assertThat(init.isValue()).isFalse();
    }

    @Test
    void integerLiteralPositive() {
        var pkg = parseHelper.parse("""
            package test
            class C { var n: int := 42 }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralInteger) pkg.classByName("C").variableByName("n").initializer();
        assertThat(init.getValue()).isEqualTo(42);
    }

    @Test
    void integerLiteralZero() {
        var pkg = parseHelper.parse("""
            package test
            class C { var n: int := 0 }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralInteger) pkg.classByName("C").variableByName("n").initializer();
        assertThat(init.getValue()).isEqualTo(0);
    }

    @Test
    void realLiteral() {
        var pkg = parseHelper.parse("""
            package test
            class C { var r: real := 3.14 }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralReal) pkg.classByName("C").variableByName("r").initializer();
        assertThat(init.getValue()).isEqualTo(3.14);
    }

    @Test
    void stringLiteral() {
        var pkg = parseHelper.parse("""
            package test
            class C { var s: string := "hello" }
            """);
        pkg.assertNoResourceErrors();
        var init = (LiteralString) pkg.classByName("C").variableByName("s").initializer();
        assertThat(init.getValue()).isEqualTo("hello");
    }

    @Test
    void nothingLiteral() {
        var pkg = parseHelper.parse("""
            package test
            class Leaf { }
            class C {
                contains x: Leaf[0..1]
                var slot: x[0..1] := nothing
            }
            """);
        pkg.assertNoResourceErrors();
        var init = pkg.classByName("C").variableByName("slot").initializer();
        assertThat(init).isInstanceOf(LiteralNothing.class);
    }

    @Test
    void infinityLiteralInRange() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                redefine tran {
                    inline for (n in 0..*) { total := total + 1 }
                }
            }
            """);
        pkg.assertNoResourceErrors();
        var infinities = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), LiteralInfinity.class);
        assertThat(infinities).hasSize(1);
    }

    @Test
    void arrayLiteralEmpty() {
        var pkg = parseHelper.parse("""
            package test
            class C { var empty: int[0] := [] }
            """);
        pkg.assertNoResourceErrors();
        var init = (ArrayLiteral) pkg.classByName("C").variableByName("empty").initializer();
        assertThat(init.getValues()).isEmpty();
    }

    @Test
    void arrayLiteralSingleElement() {
        var pkg = parseHelper.parse("""
            package test
            class C { var one: int[1] := [42] }
            """);
        pkg.assertNoResourceErrors();
        var init = (ArrayLiteral) pkg.classByName("C").variableByName("one").initializer();
        assertThat(init.getValues()).hasSize(1);
        assertThat(((LiteralInteger) init.getValues().getFirst()).getValue()).isEqualTo(42);
    }

    @Test
    void arrayLiteralMultipleElements() {
        var pkg = parseHelper.parse("""
            package test
            class C { var three: int[3] := [1, 2, 3] }
            """);
        pkg.assertNoResourceErrors();
        var init = (ArrayLiteral) pkg.classByName("C").variableByName("three").initializer();
        assertThat(init.getValues()).hasSize(3);
        assertThat(init.getValues().stream().map(e -> ((LiteralInteger) e).getValue()))
            .containsExactly(1, 2, 3);
    }
}
