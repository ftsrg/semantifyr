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
            class Holder { var value: int := 42 }
            """, "Holder", "value");

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.CONSTANT);
    }

    @Test
    void literalBoolean_isConstant() {
        var expression = firstInitializerOf("""
            package test
            class Holder { var value: bool := true }
            """, "Holder", "value");

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.CONSTANT);
    }

    @Test
    void enumLiteralReference_isConstant() {
        var expression = firstInitializerOf("""
            package test
            enum Color { Red, Green, Blue }
            class Holder { var color: Color := Color::Red }
            """, "Holder", "color");

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.CONSTANT);
    }

    @Test
    void recordLiteralOfConstants_isConstant() {
        var expression = firstInitializerOf("""
            package test
            record Point { var x: int var y: int }
            class Holder { var position: Point := Point { x = 1, y = -1 } }
            """, "Holder", "position");

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.CONSTANT);
    }

    @Test
    void recordLiteralReadingAVariable_isRuntime() {
        var expression = firstInitializerOf("""
            package test
            record Point { var x: int var y: int }
            class Holder {
                var level: int := 0
                var position: Point := Point { x = 1, y = level }
            }
            """, "Holder", "position");

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.RUNTIME);
    }

    @Test
    void featureReferenceInFeatureBound_isCompileTime() {
        var parsedPackage = parse("""
            package test
            class Holder {
                refers size: int = 3
                refers twiceSize: int = size * 2
            }
            """);
        var expression =
                parsedPackage.classByName("Holder").featureByName("twiceSize").expression();

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.COMPILE_TIME);
    }

    @Test
    void bareSelfReference_isCompileTime() {
        var parsedPackage = parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran { self.value := 1 }
            }
            """);
        var assignmentTarget = EcoreUtil2.eAllOfType(
                        parsedPackage.classByName("Holder").anonymousMain().eObject(), AssignmentOperation.class)
                .getFirst()
                .getReference();
        var bareSelf =
                EcoreUtil2.eAllOfType(assignmentTarget, SelfReference.class).getFirst();

        assertThat(modalityEvaluatorProvider.evaluate(bareSelf)).isEqualTo(Modality.COMPILE_TIME);
    }

    @Test
    void navigationToVariablePropagatesRuntime() {
        var parsedPackage = parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran { self.value := 1 }
            }
            """);
        var assignmentTarget = EcoreUtil2.eAllOfType(
                        parsedPackage.classByName("Holder").anonymousMain().eObject(), AssignmentOperation.class)
                .getFirst()
                .getReference();
        var navigation = (NavigationSuffixExpression) assignmentTarget;

        assertThat(modalityEvaluatorProvider.evaluate(navigation)).isEqualTo(Modality.RUNTIME);
    }

    @Test
    void variableReference_isRuntime() {
        var parsedPackage = parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran { value := value + 1 }
            }
            """);
        var assignment = EcoreUtil2.eAllOfType(
                        parsedPackage.classByName("Holder").anonymousMain().eObject(), AssignmentOperation.class)
                .getFirst();

        assertThat(modalityEvaluatorProvider.evaluate(assignment.getExpression()))
                .isEqualTo(Modality.RUNTIME);
    }

    @Test
    void booleanCombinatorPropagatesRuntime() {
        var parsedPackage = parse("""
            package test
            class Holder {
                var value: int := 0
                var flag: bool := false
                redefine tran { }
                prop { return AG (value > 0 && flag) }
            }
            """);
        var property = parsedPackage.classByName("Holder").anonymousProperty().expression();

        assertThat(modalityEvaluatorProvider.evaluate(property)).isEqualTo(Modality.RUNTIME);
    }

    @Test
    void constantLiteralInInitValueIsConstant() {
        var expression = firstInitializerOf("""
            package test
            class Holder { var value: int := 0 }
            """, "Holder", "value");

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.CONSTANT);
    }

    @Test
    void temporalOperatorIsRuntime() {
        var parsedPackage = parse("""
            package test
            class Holder {
                redefine tran { }
                prop { return AG true }
            }
            """);
        var expression = parsedPackage.classByName("Holder").anonymousProperty().expression();

        assertThat(modalityEvaluatorProvider.evaluate(expression)).isEqualTo(Modality.RUNTIME);
    }

    @Test
    void ifThenElseWithConstantParts_isConstant() {
        var parsedPackage = parse("""
            package test
            class Holder {
                redefine tran {
                    var result: int := 0
                    result := if true then 1 else 2
                }
            }
            """);
        var ifThenElse = EcoreUtil2.eAllOfType(
                        parsedPackage.classByName("Holder").anonymousMain().eObject(), IfThenElse.class)
                .getFirst();

        assertThat(modalityEvaluatorProvider.evaluate(ifThenElse)).isEqualTo(Modality.CONSTANT);
    }

    @Test
    void ifThenElseWithRuntimeGuard_isRuntime() {
        var parsedPackage = parse("""
            package test
            class Holder {
                var level: int := 0
                redefine tran {
                    var result: int := 0
                    result := if level > 0 then 1 else -1
                }
            }
            """);
        var ifThenElse = EcoreUtil2.eAllOfType(
                        parsedPackage.classByName("Holder").anonymousMain().eObject(), IfThenElse.class)
                .getFirst();

        assertThat(modalityEvaluatorProvider.evaluate(ifThenElse)).isEqualTo(Modality.RUNTIME);
    }

    private WrappedOxstsPackage parse(String source) {
        var parsedPackage = parseHelper.parse(source);
        parsedPackage.assertNoResourceErrors();
        return parsedPackage;
    }

    private Expression firstInitializerOf(String source, String className, String variableName) {
        var parsedPackage = parse(source);
        var wrappedClass = parsedPackage.classByName(className);
        var initializer = wrappedClass.variableByName(variableName).initializer();
        assertThat(initializer)
                .as("variable '" + variableName + "' must have an initializer")
                .isNotNull();
        return initializer;
    }
}
