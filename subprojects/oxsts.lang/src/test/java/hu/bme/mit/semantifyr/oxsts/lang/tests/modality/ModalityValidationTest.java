/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.modality;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ModalityValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void featureBoundWithLiteral_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                refers size: int = 3
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void featureBoundWithFeatureReference_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                refers size: int = 3
                refers twiceSize: int = size * 2
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void featureBoundWithVariableReference_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var runtimeValue: int := 3
                refers derivedSize: int = runtimeValue
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "compile time");
    }

    @Test
    void inlineIfGuardWithCompileTimeComparison_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                redefine tran {
                    inline if (1 < 2) { } else { }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void inlineIfGuardWithVariableReference_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran {
                    inline if (value > 0) { } else { }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "inline if");
    }

    @Test
    void inlineForOverIntegerRange_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var total: int := 0
                redefine tran {
                    inline for (index in 1..3) {
                        total := total + index
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void inlineForOverFeatureRange_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { var done: bool := false }
            class Holder {
                features elements: Item[0..*]
                contains firstItem: Item[1] subsets elements
                redefine tran {
                    inline for (element in elements) {
                        element.done := true
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void inlineIfGuardOverInlineForLoopVariable_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var total: int := 0
                redefine tran {
                    inline for (index in 1..3) {
                        inline if (index == 2) {
                            total := total + index
                        }
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void inlineIfGuardOverRuntimeForLoopVariable_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var total: int := 0
                redefine tran {
                    for (index in 1..3) {
                        inline if (index == 2) {
                            total := total + index
                        }
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "inline if");
    }

    @Test
    void assignmentToInlineForLoopVariable_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                redefine tran {
                    inline for (index in 1..3) {
                        index := 5
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INCORRECT_ASSIGNMENT, "loop variable");
    }

    @Test
    void havocOfInlineForLoopVariable_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                redefine tran {
                    inline for (index in 1..3) {
                        havoc(index)
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INCORRECT_ASSIGNMENT, "loop variable");
    }

    @Test
    void assignmentOfCompileTimeValueToVariable_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                refers limit: int = 3
                var total: int := 0
                redefine tran {
                    inline for (index in 1..3) {
                        total := index + limit
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void annotationArgumentWithConstant_passes() {
        var parsedPackage = parseHelper.parse("""
            package test
            @Tag("slow")
            class Holder { }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void annotationArgumentWithFeatureReference_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Limit(value: int)
            class Holder {
                refers limit: int = 3
                @Limit(limit)
                var value: int := 0
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "Annotation argument");
    }

    @Test
    void annotationArgumentWithVariableReference_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Limit(value: int)
            class Holder {
                var limit: int := 3
                @Limit(limit)
                var value: int := 0
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "Annotation argument");
    }

    @Test
    void inlineForOverVariable_isRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var runtimeBound: int := 0
                var total: int := 0
                redefine tran {
                    inline for (index in 1..runtimeBound) {
                        total := total + index
                    }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "inline for");
    }

    @Test
    void classVariableInitializerWithLiteralPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 42
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void classVariableInitializerWithFeatureReferencePasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                refers default: int = 3
                var value: int := default
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void classVariableInitializerWithRuntimeVariableIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var source: int := 0
                var derived: int := source
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "initializer");
    }

    @Test
    void localVariableInitializerWithRuntimeVariableIsAllowed() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var source: int := 0
                redefine tran {
                    var snapshot: int := source
                    source := snapshot + 1
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void multiplicityBoundWithIntegerLiteralPasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Host {
                contains items: Item[0..3]
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void multiplicityBoundWithFeatureReferencePasses() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Host {
                refers size: int = 3
                contains items: Item[size]
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void multiplicityBoundWithRuntimeVariableIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Item { }
            class Host {
                var runtimeSize: int := 3
                contains items: Item[runtimeSize]
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "Multiplicity");
    }

    @Test
    void wrapperExposesClassAndFeaturesAfterParse() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker { var done: bool := false }
            class Holder {
                contains firstWorker: Worker[1]
                contains secondWorker: Worker[1]
            }
            """);

        parsedPackage.assertNoResourceErrors();
        var holder = parsedPackage.classByName("Holder");
        assertThat(holder.features()).hasSize(2);
        assertThat(holder.featureByName("firstWorker").kind().toString()).isEqualTo("CONTAINMENT");
        assertThat(holder.featureByName("firstWorker").typeDomain())
                .isSameAs(parsedPackage.classByName("Worker").eObject());
    }
}
