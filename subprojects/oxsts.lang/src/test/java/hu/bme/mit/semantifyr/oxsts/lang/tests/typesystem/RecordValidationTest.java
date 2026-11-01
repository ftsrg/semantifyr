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
import org.eclipse.xtext.validation.Issue;
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

    @Test
    void recordLiteralsAreAcceptedWhereRecordValuesAre() {
        var pkg = parseHelper.parse(RECORDS + """
            record Box {
                var corner: Point := Point { x = 1 }
                var size: int := 1
            }
            class C {
                var p: Point := Point { x = 1 }
                var s: Segment := Segment { from = Point { x = 0, y = 1 }, to = Point { x = 2 } }
                var b: Box
                tran move(target: Point) {
                    p := target
                }
                prop isAt(point: Point): bool {
                    return p == point
                }
                redefine tran {
                    p := Point { x = p.x + 1, y = p.y }
                    var local: Point := Point { x = s.to.x }
                    inline move(Point { x = local.y })
                    s := Segment { from = p, to = local, color = Color::Green }
                    b := Box { size = 2 }
                    p := if p.x > 3 then Point { x = 0 } else p
                }
                prop {
                    return EF (p != Point { x = 3, y = 0 } && isAt(Point { x = 1 }) && Point { x = 1 }.x == 1)
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertNoValidationErrors();
    }

    @Test
    void fieldAssignedTwiceIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point := Point { x = 1, x = 2 }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        pkg.assertHasValidationIssue(OxstsValidator.DUPLICATE_NAME_ISSUE, "'x' is assigned more than once");
    }

    @Test
    void omittedFieldWithoutDefaultIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point := Point { y = 1 }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.INCOMPLETE_RECORD_LITERAL, "leaves 'x' without a value");
    }

    @Test
    void omittedRecordFieldWhoseRecordLacksADefaultIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var s: Segment := Segment { from = Point { x = 1 } }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.INCOMPLETE_RECORD_LITERAL, "leaves 'to' without a value");
    }

    @Test
    void fieldOfAnotherRecordDoesNotResolve() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point := Point { x = 1, color = Color::Red }
            }
            """, CheckMode.ALL);

        assertThat(pkg.getResourceErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getMessage()).contains("'color'"));
    }

    @Test
    void literalOfAClassDoesNotResolve() {
        var pkg = parseHelper.parse(RECORDS + """
            class Worker {
                var x: int
            }
            class C {
                var p: Point := Worker { x = 1 }
            }
            """, CheckMode.ALL);

        assertThat(pkg.getResourceErrors()).isNotEmpty();
    }

    @Test
    void fieldValueOfAnotherTypeIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point := Point { x = true }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.TYPE_MISMATCH, "not assignable to field 'x'");
    }

    @Test
    void literalOfAnotherRecordIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                redefine tran { p := Segment { from = p, to = p } }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.TYPE_MISMATCH, "Cannot assign");
    }

    @Test
    void classLevelLiteralOfRuntimeValuesIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var n: int := 0
                var p: Point := Point { x = n }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "RUNTIME");
    }

    @Test
    void literalIsNoWriteTarget() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                redefine tran {
                    havoc(Point { x = 1 })
                    Point { x = 1 } := p
                    Point { x = 1 }.x := 2
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertThat(pkg.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .hasSize(3)
                .allSatisfy(issue -> assertThat(issue.getCode()).isEqualTo(OxstsValidator.INCORRECT_ASSIGNMENT));
    }

    @Test
    void literalEvaluatedAtCompileTimeIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                refers same: bool = Point { x = 1 } == Point { x = 1 }
                redefine tran {
                    inline if (Point { x = 1 } != Point { x = 2 }) { }
                }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.UNSUPPORTED_FEATURE, "not yet evaluated at compile time");
        assertThat(pkg.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .extracting(Issue::getMessage)
                .anySatisfy(message -> assertThat(message).contains("a feature binding"))
                .anySatisfy(message -> assertThat(message).contains("an `inline if` guard"));
    }

    @Test
    void orderingComparisonWithLiteralIsRejected() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                prop { return EF p < Point { x = 1 } }
            }
            """, CheckMode.ALL);

        pkg.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "only be compared with == and !=");
    }

    @Test
    void recordNameIsNoValue() {
        var pkg = parseHelper.parse(RECORDS + """
            class C {
                var p: Point
                redefine tran { p := Point {} }
            }
            """, CheckMode.ALL);

        pkg.assertNoResourceErrors();
        assertOnlyErrorsAre(pkg, OxstsValidator.TYPE_MISMATCH, "'Point' is a record type, not a value");
    }

    private static void assertOnlyErrorsAre(WrappedOxstsPackage pkg, String code, String messageSubstring) {
        assertThat(pkg.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(code);
                    assertThat(issue.getMessage()).contains(messageSubstring);
                });
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
