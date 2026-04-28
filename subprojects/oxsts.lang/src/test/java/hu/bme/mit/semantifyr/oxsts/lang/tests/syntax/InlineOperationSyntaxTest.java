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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineChoiceFor;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineSeqFor;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class InlineOperationSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void inlineCall() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                tran step() { x := x + 1 }
                redefine tran { inline step() }
            }
            """);
        pkg.assertNoResourceErrors();
        var calls = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), InlineCall.class);
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().getCallExpression()).isNotNull();
    }

    @Test
    void inlineIfWithElse() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { inline if (1 < 2) { x := 1 } else { x := 2 } }
            }
            """);
        pkg.assertNoResourceErrors();
        var ifs = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), InlineIfOperation.class);
        assertThat(ifs).hasSize(1);
        var op = ifs.getFirst();
        assertThat(op.getGuard()).isNotNull();
        assertThat(op.getBody()).isNotNull();
        assertThat(op.getElse()).isNotNull();
    }

    @Test
    void inlineIfWithoutElse() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { inline if (1 < 2) { x := 1 } }
            }
            """);
        pkg.assertNoResourceErrors();
        var ifs = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), InlineIfOperation.class);
        assertThat(ifs).hasSize(1);
        assertThat(ifs.getFirst().getElse()).isNull();
    }

    @Test
    void inlineSeqForBare() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                redefine tran { inline for (n in 1..3) { total := total + 1 } }
            }
            """);
        pkg.assertNoResourceErrors();
        var fors = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), InlineSeqFor.class);
        assertThat(fors).hasSize(1);
        var f = fors.getFirst();
        assertThat(f.getLoopVariable().getName()).isEqualTo("n");
        assertThat(f.getRangeExpression()).isNotNull();
        assertThat(f.getBody()).isNotNull();
        assertThat(f.getElse()).isNull();
    }

    @Test
    void inlineSeqForExplicitSeqKeyword() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                redefine tran { inline for seq (n in 1..3) { total := total + 1 } }
            }
            """);
        pkg.assertNoResourceErrors();
        var fors = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), InlineSeqFor.class);
        assertThat(fors).hasSize(1);
    }

    @Test
    void inlineSeqForWithElse() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                redefine tran {
                    inline for (n in 1..0) { total := total + 1 }
                    else { total := -1 }
                }
            }
            """);
        pkg.assertNoResourceErrors();
        var fors = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), InlineSeqFor.class);
        assertThat(fors).hasSize(1);
        assertThat(fors.getFirst().getElse()).isNotNull();
    }

    @Test
    void inlineChoiceFor() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var chosen: int := 0
                redefine tran { inline for choice (n in 1..3) { chosen := n } }
            }
            """);
        pkg.assertNoResourceErrors();
        var fors = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), InlineChoiceFor.class);
        assertThat(fors).hasSize(1);
        assertThat(fors.getFirst().getLoopVariable().getName()).isEqualTo("n");
    }

    @Test
    void inlineChoiceForWithElse() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var chosen: int := 0
                redefine tran {
                    inline for choice (n in 1..0) { chosen := n }
                    else { chosen := -1 }
                }
            }
            """);
        pkg.assertNoResourceErrors();
        var fors = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), InlineChoiceFor.class);
        assertThat(fors).hasSize(1);
        assertThat(fors.getFirst().getElse()).isNotNull();
    }
}
