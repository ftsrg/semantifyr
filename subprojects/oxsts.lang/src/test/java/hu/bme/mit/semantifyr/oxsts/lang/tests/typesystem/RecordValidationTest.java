/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.typesystem;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.WrappedOxstsPackage;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class RecordValidationTest {

    private static final String RECORDS = """
            package test
            enum Color { Red, Green }
            record Point {
                var x: int
                var y: int := 0
            }
            record Segment {
                var from: Point
                var to: Point
                var color: Color := Color::Red
            }
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void recordFieldsAreReadWrittenComparedAndHavoced() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                var q: Point
                var s: Segment
                tran move(target: Point) {
                    p := target
                }
                prop isOrigin(point: Point): bool {
                    return point.x == 0 && point.y == 0
                }
                redefine tran {
                    p.x := p.x + 1
                    q := p
                    s.from.y := s.to.y
                    s.to := q
                    havoc(q)
                    var local: Point := p
                    local.y := 2
                    inline move(local)
                }
                prop {
                    return EF (p == q && s.from != s.to && isOrigin(s.from) && s.color == Color::Green)
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void writingAFieldOfARecordParameterIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                tran reset(target: Point) {
                    target.x := 0
                    havoc(target.y)
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertThat(pkg.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .hasSize(2)
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.INCORRECT_ASSIGNMENT);
                    assertThat(issue.getMessage()).contains("passed by value");
                });
    }

    @Test
    void recursiveRecordIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            record Node {
                var value: int
                var next: Node
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.RECURSIVE_RECORD, "'Node'");
    }

    @Test
    void mutuallyRecursiveRecordsAreRejected() {
        var pkg = parseHelper.parse("""
            package test
            record Left {
                var right: Right
            }
            record Right {
                var left: Left
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertThat(pkg.getIssues())
                .filteredOn(issue -> OxstsValidator.RECURSIVE_RECORD.equals(issue.getCode()))
                .hasSize(2);
    }

    @Test
    void orderingComparisonOfRecordsIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                var q: Point
                prop { return EF p < q }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "only be compared with == and !=");
    }

    @Test
    void recordComparedWithNothingIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                prop { return EF p != nothing }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "only be compared with == and !=");
    }

    @Test
    void recordComparedWithIntegerIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                prop { return EF p == 1 }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "Cannot compare");
    }

    @Test
    void recordAssignedFromOtherRecordTypeIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                var s: Segment
                redefine tran { p := s }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "Cannot assign");
    }

    @Test
    void classLevelRecordInitializedFromVariableIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                var q: Point := p
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH);
    }

    @Test
    void recordConstructedByCallIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                redefine tran { p := Point(1, 2) }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.INCORRECT_CALLED_ELEMENT, "not callable");
    }

    @Test
    void arrayOfRecordsIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var points: Point[2]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAreUnsupported(pkg, "Arrays of records");
    }

    @Test
    void optionalRecordIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point[0..1]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAreUnsupported(pkg, "exactly one");
    }

    @Test
    void arrayFieldInRecordIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            record Samples {
                var values: int[3]
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAreUnsupported(pkg, "Array fields");
    }

    @Test
    void classTypedRecordFieldIsRejected() {
        var pkg = parseHelper.parse("""
            package test
            class Worker { }
            record Assignment {
                var worker: Worker
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAreUnsupported(pkg, "data, enum or record type");
    }

    @Test
    void recordTypedFeatureIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                refers origin: Point
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAreUnsupported(pkg, "Record-typed features");
    }

    private static void assertOnlyErrorsAreUnsupported(WrappedOxstsPackage pkg, String messageSubstring) {
        assertThat(pkg.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.UNSUPPORTED_FEATURE);
                    assertThat(issue.getMessage()).contains(messageSubstring);
                });
    }
}
