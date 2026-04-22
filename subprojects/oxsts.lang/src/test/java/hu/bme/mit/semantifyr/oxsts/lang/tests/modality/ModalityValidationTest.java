/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.modality;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@InjectWithOxsts
public class ModalityValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void featureBoundWithLiteral_passes() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                refers size: int = 3
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void featureBoundWithFeatureReference_passes() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                refers size: int = 3
                refers twiceSize: int = size * 2
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void featureBoundWithVariableReference_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var runtimeValue: int := 3
                refers derivedSize: int = runtimeValue
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "compile time");
    }

    @Test
    void inlineIfGuardWithCompileTimeComparison_passes() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                redefine tran {
                    inline if (1 < 2) { } else { }
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void inlineIfGuardWithVariableReference_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran {
                    inline if (x > 0) { } else { }
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "inline if");
    }

    @Test
    void inlineForOverIntegerRange_passes() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var total: int := 0
                redefine tran {
                    inline for (n in 1..3) {
                        total := total + n
                    }
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void inlineForOverFeatureRange_passes() {
        var pkg = parseHelper.parse("""
            package test
            class Item { var done: bool := false }
            class C {
                features elements: Item[0..*]
                contains e1: Item[1] subsets elements
                redefine tran {
                    inline for (it in elements) {
                        it.done := true
                    }
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void inlineForOverVariable_isRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var runtimeBound: int := 0
                var total: int := 0
                redefine tran {
                    inline for (n in 1..runtimeBound) {
                        total := total + n
                    }
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "inline for");
    }

    @Test
    void classVariableInitializerWithLiteralPasses() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 42
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void classVariableInitializerWithFeatureReferencePasses() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                refers default: int = 3
                var x: int := default
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void classVariableInitializerWithRuntimeVariableIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var source: int := 0
                var derived: int := source
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "initializer");
    }

    @Test
    void localVariableInitializerWithRuntimeVariableIsAllowed() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var source: int := 0
                redefine tran {
                    var snapshot: int := source
                    source := snapshot + 1
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void multiplicityBoundWithIntegerLiteralPasses() {
        var pkg = parseHelper.parse("""
            package test
            class Item { }
            class Host {
                contains items: Item[0..3]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void multiplicityBoundWithFeatureReferencePasses() {
        var pkg = parseHelper.parse("""
            package test
            class Item { }
            class Host {
                refers size: int = 3
                contains items: Item[size]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void multiplicityBoundWithRuntimeVariableIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Item { }
            class Host {
                var runtimeSize: int := 3
                contains items: Item[runtimeSize]
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "Multiplicity");
    }

    @Test
    void wrapperExposesClassAndFeaturesAfterParse() {
        var pkg = parseHelper.parse("""
            package test
            class Worker { var done: bool := false }
            class Holder {
                contains w1: Worker[1]
                contains w2: Worker[1]
            }
            """);

        pkg.assertNoResourceErrors();
        var holder = pkg.classByName("Holder");
        assertThat(holder.features()).hasSize(2);
        assertThat(holder.featureByName("w1").kind().toString()).isEqualTo("CONTAINMENT");
        assertThat(holder.featureByName("w1").typeDomain()).isSameAs(pkg.classByName("Worker").eObject());
    }
}
