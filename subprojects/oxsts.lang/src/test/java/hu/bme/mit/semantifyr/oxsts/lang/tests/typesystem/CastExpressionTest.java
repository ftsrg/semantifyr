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
public class CastExpressionTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void castIntToAnyIsRejectedAsWidening() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var x: int := 0
                redefine tran {
                    var result: any := 0
                    result := x as any
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void castAnyToIntIsAcceptedAsNarrowingCast() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var a: any := 0
                redefine tran {
                    var n: int := 0
                    n := a as int
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void castToUnrelatedPrimitiveTypeIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class C {
                var b: bool := false
                redefine tran {
                    var n: int := 0
                    n := b as int
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void castBaseToChildIsAccepted() {
        var pkg = parseHelper.parse("""
            package test
            class Base { }
            class Child : Base { }
            class Host {
                contains bases: Base[0..*]
                contains child: Child[1] subsets bases
                var ref: bases[0..1] := nothing
                redefine tran {
                    var c: Child[0..1] := nothing
                    c := ref as Child
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void castBetweenUnrelatedClassesIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class A { }
            class B { }
            class Host {
                contains a: A[1]
                redefine tran {
                    var b: B[0..1] := nothing
                    b := a as B
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void castChildToBaseIsRejectedAsWidening() {
        var pkg = parseHelper.parse("""
            package test
            class Base { }
            class Child : Base { }
            class Host {
                contains child: Child[1]
                redefine tran {
                    var b: Base[0..1] := nothing
                    b := child as Base
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void narrowingMultiplicityAnyToOneIsAccepted() {
        var pkg = parseHelper.parse("""
            package test
            class Leaf { }
            class Host {
                contains leaves: Leaf[0..*]
                redefine tran {
                    var one: Leaf[0..1] := nothing
                    one := leaves as Leaf[1]
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void narrowingMultiplicityOptionalToOneIsAccepted() {
        var pkg = parseHelper.parse("""
            package test
            class Leaf { }
            class Host {
                contains maybe: Leaf[0..1]
                redefine tran {
                    var one: Leaf[0..1] := nothing
                    one := maybe as Leaf[1]
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void narrowingMultiplicityAnyToOptionalIsAccepted() {
        var pkg = parseHelper.parse("""
            package test
            class Leaf { }
            class Host {
                contains leaves: Leaf[0..*]
                redefine tran {
                    var maybe: Leaf[0..1] := nothing
                    maybe := leaves as Leaf[0..1]
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void wideningMultiplicityOneToAnyIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Leaf { }
            class Host {
                contains one: Leaf[1]
                redefine tran {
                    var many: Leaf[0..*] := nothing
                    many := one as Leaf[0..*]
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void wideningMultiplicityOneToOptionalIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Leaf { }
            class Host {
                contains one: Leaf[1]
                redefine tran {
                    var maybe: Leaf[0..1] := nothing
                    maybe := one as Leaf[0..1]
                }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void sameMultiplicitySameDomainIsAccepted() {
        var pkg = parseHelper.parse("""
            package test
            class Leaf { }
            class Host {
                contains one: Leaf[1]
                redefine tran {
                    var other: Leaf[0..1] := nothing
                    other := one as Leaf[1]
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }
}
