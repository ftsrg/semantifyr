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
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var value: int := 0
                redefine tran {
                    var result: any := 0
                    result := value as any
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void castAnyToIntIsAcceptedAsNarrowingCast() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var anyValue: any := 0
                redefine tran {
                    var intValue: int := 0
                    intValue := anyValue as int
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void castToUnrelatedPrimitiveTypeIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Holder {
                var flag: bool := false
                redefine tran {
                    var intValue: int := 0
                    intValue := flag as int
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void castBaseToChildIsAccepted() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Base { }
            class Child : Base { }
            class Host {
                contains bases: Base[0..*]
                contains child: Child[1] subsets bases
                var selected: bases[0..1] := nothing
                redefine tran {
                    var narrowed: Child[0..1] := nothing
                    narrowed := selected as Child
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void castBetweenUnrelatedClassesIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Sensor { }
            class Motor { }
            class Host {
                contains sensor: Sensor[1]
                redefine tran {
                    var motor: Motor[0..1] := nothing
                    motor := sensor as Motor
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void castChildToBaseIsRejectedAsWidening() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Base { }
            class Child : Base { }
            class Host {
                contains child: Child[1]
                redefine tran {
                    var base: Base[0..1] := nothing
                    base := child as Base
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void narrowingMultiplicityAnyToOneIsAccepted() {
        var parsedPackage = parseHelper.parse("""
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

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void narrowingMultiplicityOptionalToOneIsAccepted() {
        var parsedPackage = parseHelper.parse("""
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

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void narrowingMultiplicityAnyToOptionalIsAccepted() {
        var parsedPackage = parseHelper.parse("""
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

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void wideningMultiplicityOneToAnyIsRejected() {
        var parsedPackage = parseHelper.parse("""
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

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void wideningMultiplicityOneToOptionalIsRejected() {
        var parsedPackage = parseHelper.parse("""
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

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void sameMultiplicitySameDomainIsAccepted() {
        var parsedPackage = parseHelper.parse("""
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

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void narrowingCastInsideAPropertyIsAccepted() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Base { }
            class Child : Base { }
            class Host {
                contains items: Base[0..*]
                contains child: Child[1] subsets items
                var selected: items[0..1] := nothing
                prop { return AG (selected == nothing || (selected as Child) == child) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void wideningCastInsideAPropertyIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Base { }
            class Child : Base { }
            class Host {
                contains child: Child[1]
                prop { return AG (child as Base) == child }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INVALID_CAST, "narrowing");
    }

    @Test
    void castOfATemporalPropertyToBoolIsAccepted() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Host {
                var value: int := 0
                prop { return (AG value == 0) as bool }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }
}
