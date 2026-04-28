/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.modality;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.ExpressionModalityEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.Modality;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.WrappedOxstsPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ExpressionModalityEvaluatorTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private ExpressionModalityEvaluatorProvider modalityEvaluatorProvider;

    @Test
    void literalInteger_isConstant() {
        var expression = firstInitializerOf("""
            package test
            class C { var x: int := 42 }
            """, "C", "x");

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.CONSTANT);
    }

    @Test
    void literalBoolean_isConstant() {
        var expression = firstInitializerOf("""
            package test
            class C { var x: bool := true }
            """, "C", "x");

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.CONSTANT);
    }

    @Test
    void enumLiteralReference_isConstant() {
        var expression = firstInitializerOf("""
            package test
            enum Color { Red, Green, Blue }
            class C { var c: Color := Color::Red }
            """, "C", "c");

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.CONSTANT);
    }

    @Test
    void featureReferenceInFeatureBound_isCompileTime() {
        var pkg = parse("""
            package test
            class C {
                refers size: int = 3
                refers twiceSize: int = size * 2
            }
            """);
        var expression = pkg.classByName("C").featureByName("twiceSize").expression();

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.COMPILE_TIME);
    }

    @Test
    void bareSelfReference_isCompileTime() {
        var pkg = parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { self.x := 1 }
            }
            """);
        var assignmentLhs = EcoreUtil2.eAllOfType(
                        pkg.classByName("C").anonymousMain().eObject(), AssignmentOperation.class)
                .getFirst()
                .getReference();
        var bareSelf = EcoreUtil2.eAllOfType(assignmentLhs, SelfReference.class).getFirst();

        assertThat(modalityEvaluatorProvider.evaluate(bareSelf)).isEqualTo(Modality.COMPILE_TIME);
    }

    @Test
    void navigationToVariablePropagatesRuntime() {
        var pkg = parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { self.x := 1 }
            }
            """);
        var assignmentLhs = EcoreUtil2.eAllOfType(
                        pkg.classByName("C").anonymousMain().eObject(), AssignmentOperation.class)
                .getFirst()
                .getReference();
        var navigation = (NavigationSuffixExpression) assignmentLhs;

        assertThat(modalityEvaluatorProvider.evaluate(navigation)).isEqualTo(Modality.RUNTIME);
    }

    @Test
    void variableReference_isRuntime() {
        var pkg = parse("""
            package test
            class C {
                var x: int := 0
                redefine tran { x := x + 1 }
            }
            """);
        var assignment = EcoreUtil2.eAllOfType(
                        pkg.classByName("C").anonymousMain().eObject(), AssignmentOperation.class)
                .getFirst();

        assertThat(modalityEvaluatorProvider.evaluate(assignment.getExpression()))
                .isEqualTo(Modality.RUNTIME);
    }

    @Test
    void booleanCombinatorPropagatesRuntime() {
        var pkg = parse("""
            package test
            class C {
                var x: int := 0
                var b: bool := false
                redefine tran { }
                prop { return AG (x > 0 && b) }
            }
            """);
        var ag = pkg.classByName("C").anonymousProperty().expression();

        assertThat(modalityEvaluatorProvider.evaluate(ag)).isEqualTo(Modality.RUNTIME);
    }

    @Test
    void constantLiteralInInitValueIsConstant() {
        var expression = firstInitializerOf("""
            package test
            class C { var x: int := 0 }
            """, "C", "x");

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.CONSTANT);
    }

    @Test
    void temporalOperatorIsRuntime() {
        var pkg = parse("""
            package test
            class C {
                redefine tran { }
                prop { return AG true }
            }
            """);
        var expression = pkg.classByName("C").anonymousProperty().expression();

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.RUNTIME);
    }

    @Test
    void ifThenElseWithConstantParts_isConstant() {
        var pkg = parse("""
            package test
            class C {
                redefine tran {
                    var result: int := 0
                    result := if true then 1 else 2
                }
            }
            """);
        var ite = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), IfThenElse.class)
                .getFirst();

        assertThat(modalityEvaluatorProvider.evaluate(ite)).isEqualTo(Modality.CONSTANT);
    }

    @Test
    void ifThenElseWithRuntimeGuard_isRuntime() {
        var pkg = parse("""
            package test
            class C {
                var n: int := 0
                redefine tran {
                    var result: int := 0
                    result := if n > 0 then 1 else -1
                }
            }
            """);
        var ite = EcoreUtil2.eAllOfType(pkg.classByName("C").anonymousMain().eObject(), IfThenElse.class)
                .getFirst();

        assertThat(modalityEvaluatorProvider.evaluate(ite)).isEqualTo(Modality.RUNTIME);
    }

    private WrappedOxstsPackage parse(String source) {
        var pkg = parseHelper.parse(source);
        pkg.assertNoResourceErrors();
        return pkg;
    }

    private Expression firstInitializerOf(String source, String className, String variableName) {
        var pkg = parse(source);
        var cls = pkg.classByName(className);
        var initializer = cls.variableByName(variableName).initializer();
        assertThat(initializer)
                .as("variable '" + variableName + "' must have an initializer")
                .isNotNull();
        return initializer;
    }
}
