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
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := 5 }
            }
            """);
        pkg.assertNoResourceErrors();
        var ops = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), AssignmentOperation.class);
        assertThat(ops).hasSize(1);
        assertThat(ops.getFirst().getReference()).isNotNull();
        assertThat(ops.getFirst().getExpression()).isNotNull();
    }

    @Test
    void assumptionOperation() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { assume(x > 0); x := 1 }
            }
            """);
        pkg.assertNoResourceErrors();
        var ops = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), AssumptionOperation.class);
        assertThat(ops).hasSize(1);
        assertThat(ops.getFirst().getExpression()).isNotNull();
    }

    @Test
    void havocOperation() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                redefine tran { havoc(b) }
            }
            """);
        pkg.assertNoResourceErrors();
        var ops = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), HavocOperation.class);
        assertThat(ops).hasSize(1);
        assertThat(ops.getFirst().getReference()).isNotNull();
    }

    @Test
    void choiceOperationWithTwoBranches() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { choice { x := 1 } or { x := 2 } }
            }
            """);
        pkg.assertNoResourceErrors();
        var choices = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), ChoiceOperation.class);
        assertThat(choices).hasSize(1);
        assertThat(choices.getFirst().getBranches()).hasSize(2);
    }

    @Test
    void choiceOperationWithManyBranches() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran {
                    choice { x := 1 } or { x := 2 } or { x := 3 } or { x := 4 }
                }
            }
            """);
        pkg.assertNoResourceErrors();
        var choices = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), ChoiceOperation.class);
        assertThat(choices).hasSize(1);
        assertThat(choices.getFirst().getBranches()).hasSize(4);
    }

    @Test
    void sequenceOperationFromBlock() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                var y: int := 0
                redefine tran { x := 1; y := 2 }
            }
            """);
        pkg.assertNoResourceErrors();
        var seqs = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), SequenceOperation.class);
        assertThat(seqs).isNotEmpty();
        var outer = pkg.classByName("C").anonymousMain().eObject().getBranches().getFirst();
        assertThat(outer.getSteps()).hasSize(2);
    }

    @Test
    void ifOperationWithElse() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                var x: int := 0
                redefine tran { if (b) { x := 1 } else { x := 0 } }
            }
            """);
        pkg.assertNoResourceErrors();
        var ifs = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), IfOperation.class);
        assertThat(ifs).hasSize(1);
        assertThat(ifs.getFirst().getGuard()).isNotNull();
        assertThat(ifs.getFirst().getBody()).isNotNull();
        assertThat(ifs.getFirst().getElse()).isNotNull();
    }

    @Test
    void ifOperationWithoutElse() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                var x: int := 0
                redefine tran { if (b) { x := 1 } }
            }
            """);
        pkg.assertNoResourceErrors();
        var ifs = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), IfOperation.class);
        assertThat(ifs).hasSize(1);
        assertThat(ifs.getFirst().getElse()).isNull();
    }

    @Test
    void runtimeForOverIntegerRange() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                redefine tran { for (n in 1..3) { total := total + 1 } }
            }
            """);
        pkg.assertNoResourceErrors();
        var fors = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), ForOperation.class);
        assertThat(fors).hasSize(1);
        var f = fors.getFirst();
        assertThat(f.getLoopVariable()).isNotNull();
        assertThat(f.getLoopVariable().getName()).isEqualTo("n");
        assertThat(f.getRangeExpression()).isNotNull();
        assertThat(f.getBody()).isNotNull();
    }

    @Test
    void localVariableDeclarationWithInitializer() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran {
                    var tmp: int := 5
                    x := tmp
                }
            }
            """);
        pkg.assertNoResourceErrors();
        var locals = EcoreUtil2.eAllOfType(
                pkg.classByName("C").anonymousMain().eObject(), LocalVarDeclarationOperation.class);
        assertThat(locals).hasSize(1);
        assertThat(locals.getFirst().getName()).isEqualTo("tmp");
        assertThat(locals.getFirst().getTypeSpecification()).isNotNull();
        assertThat(locals.getFirst().getExpression()).isNotNull();
    }

    @Test
    void localVariableDeclarationWithTypeOnly() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran {
                    var tmp: int
                    havoc(tmp)
                    x := tmp
                }
            }
            """);
        pkg.assertNoResourceErrors();
        var locals = EcoreUtil2.eAllOfType(
                pkg.classByName("C").anonymousMain().eObject(), LocalVarDeclarationOperation.class);
        assertThat(locals).hasSize(1);
        assertThat(locals.getFirst().getExpression()).isNull();
    }

    @Test
    void transitionWithMultipleOrBranches() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := 1 } or { x := 2 } or { x := 3 }
            }
            """);
        pkg.assertNoResourceErrors();
        var main = pkg.classByName("C").anonymousMain().eObject();
        assertThat(main.getBranches()).hasSize(3);
    }
}
