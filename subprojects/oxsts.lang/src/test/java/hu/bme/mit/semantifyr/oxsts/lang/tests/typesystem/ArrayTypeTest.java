/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.typesystem;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ArrayTypeTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void indexingWithIntConstantPasses() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[3] := [0, 0, 0]
                redefine tran {
                    values[0] := 1
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void indexingWithIntVariablePasses() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[3] := [0, 0, 0]
                var i: int := 0
                redefine tran {
                    values[i] := 1
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void indexingWithBoolIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[3] := [0, 0, 0]
                var b: bool := false
                redefine tran {
                    values[b] := 1
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "numeric");
    }

    @Test
    void arrayLiteralWithHomogeneousElementsPasses() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[3] := [1, 2, 3]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void arrayLiteralWithMixedTypesIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[2] := [1, true]
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "common type");
    }

    @Test
    void variableWithUnboundedArrayIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var buffer: int[*] := [0]
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "compile-time upper bound");
    }

    @Test
    void indexedExpressionTypeIsTheElementType() {
        // Assignment of `values[0]` (int element) to an int variable works.
        // If we didn't reduce the array's multiplicity to ONE on indexing,
        // this would flag a multiplicity mismatch.
        var pkg = parseHelper.parse("""
            package test
            class C {
                var values: int[3] := [1, 2, 3]
                var first: int := 0
                redefine tran {
                    first := values[0]
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void indexingIntoScalarIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var scalar: int := 0
                redefine tran {
                    scalar[0] := 1
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "non-array");
    }
}
