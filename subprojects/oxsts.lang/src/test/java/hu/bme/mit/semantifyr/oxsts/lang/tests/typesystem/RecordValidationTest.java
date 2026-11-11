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
                var x: int := 0
                var y: int := 0
            }
            record Segment {
                var from: Point := Point { x = 0 }
                var to: Point := Point { x = 0 }
                var color: Color := Color::Red
            }
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void recordFieldsAreReadWrittenComparedAndHavoced() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 0 }
                var previous: Point := Point { x = 0 }
                var segment: Segment := Segment { color = Color::Red }
                tran move(target: Point) {
                    position := target
                }
                prop isOrigin(point: Point): bool {
                    return point.x == 0 && point.y == 0
                }
                redefine tran {
                    position.x := position.x + 1
                    previous := position
                    segment.from.y := segment.to.y
                    segment.to := previous
                    havoc(previous)
                    var local: Point := position
                    local.y := 2
                    inline move(local)
                }
                prop {
                    return EF (position == previous && segment.from != segment.to && isOrigin(segment.from) && segment.color == Color::Green)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void recordParameterTakesOnlyARecordOfItsType() {
        var parsedPackage = parseHelper.parse("""
            package test
            record Point {
                var x: int := 0
            }
            record Size {
                var x: int := 0
            }
            class Holder {
                var position: Point := Point { x = 1 }
                var size: Size := Size { x = 1 }
                var sum: int := 0
                tran add(point: Point) { sum := sum + point.x }
                redefine tran {
                    inline add(position)
                    inline add(Point { x = 2 })
                    inline add(size)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.TYPE_MISMATCH);
                    assertThat(issue.getLineNumber()).isEqualTo(16);
                });
    }

    @Test
    void writingAFieldOfARecordParameterIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                tran reset(target: Point) {
                    target.x := 0
                    havoc(target.y)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .hasSize(2)
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.INCORRECT_ASSIGNMENT);
                    assertThat(issue.getMessage()).contains("passed by value");
                });
    }

    @Test
    void recursiveRecordIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            record Node {
                var value: int := 0
                var next: Node
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.RECURSIVE_RECORD, "'Node'");
    }

    @Test
    void mutuallyRecursiveRecordsAreRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            record Left {
                var right: Right
            }
            record Right {
                var left: Left
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> OxstsValidator.RECURSIVE_RECORD.equals(issue.getCode()))
                .hasSize(2);
    }

    @Test
    void orderingComparisonOfRecordsIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 0 }
                var previous: Point := Point { x = 0 }
                prop { return EF position < previous }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "only be compared with == and !=");
    }

    @Test
    void recordComparedWithNothingIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 0 }
                prop { return EF position != nothing }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "only be compared with == and !=");
    }

    @Test
    void recordComparedWithIntegerIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 0 }
                prop { return EF position == 1 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "Cannot compare");
    }

    @Test
    void recordAssignedFromOtherRecordTypeIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 0 }
                var segment: Segment := Segment { color = Color::Red }
                redefine tran { position := segment }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "Cannot assign");
    }

    @Test
    void classLevelRecordInitializedFromVariableIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 0 }
                var previous: Point := position
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH);
    }

    @Test
    void recordConstructedByCallIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 0 }
                redefine tran { position := Point(1, 2) }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.INCORRECT_CALLED_ELEMENT, "not callable");
    }

    @Test
    void arrayOfRecordsIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var points: Point[2] := [Point { x = 0 }, Point { x = 0 }]
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAreUnsupported(parsedPackage, "Arrays of records");
    }

    @Test
    void optionalRecordIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point[0..1] := Point { x = 0 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAreUnsupported(parsedPackage, "exactly one");
    }

    @Test
    void arrayFieldInRecordIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            record Samples {
                var values: int[3] := [0, 0, 0]
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAreUnsupported(parsedPackage, "Array fields");
    }

    @Test
    void classTypedRecordFieldIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Worker { }
            record Assignment {
                var worker: Worker
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "data, enum or record type");
    }

    @Test
    void recordTypedFeatureIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                refers origin: Point
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAreUnsupported(parsedPackage, "Record-typed features");
    }

    @Test
    void globalRecordTypedFeatureIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            global reference origin: Point
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAreUnsupported(parsedPackage, "Record-typed features");
    }

    @Test
    void localArrayOfRecordsIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                redefine tran {
                    var points: Point[2] := [Point { x = 0 }, Point { x = 1 }]
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAreUnsupported(parsedPackage, "Arrays of records");
    }

    @Test
    void recordLiteralsAreAcceptedWhereRecordValuesAre() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            record Box {
                var corner: Point := Point { x = 1 }
                var size: int := 1
            }
            class Holder {
                var position: Point := Point { x = 1 }
                var segment: Segment := Segment { from = Point { x = 0, y = 1 }, to = Point { x = 2 } }
                var box: Box := Box { size = 1 }
                tran move(target: Point) {
                    position := target
                }
                prop isAt(point: Point): bool {
                    return position == point
                }
                redefine tran {
                    position := Point { x = position.x + 1, y = position.y }
                    var local: Point := Point { x = segment.to.x }
                    inline move(Point { x = local.y })
                    segment := Segment { from = position, to = local, color = Color::Green }
                    box := Box { size = 2 }
                    position := if position.x > 3 then Point { x = 0 } else position
                }
                prop {
                    return EF (position != Point { x = 3, y = 0 } && isAt(Point { x = 1 }) && Point { x = 1 }.x == 1)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void fieldAssignedTwiceIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 1, x = 2 }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.DUPLICATE_NAME_ISSUE, "'x' is assigned more than once");
    }

    @Test
    void fieldOfAnotherRecordDoesNotResolve() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 1, color = Color::Red }
            }
            """, CheckMode.ALL);

        assertThat(parsedPackage.getResourceErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getMessage()).contains("'color'"));
    }

    @Test
    void literalOfAClassDoesNotResolve() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Worker {
                var x: int := 0
            }
            class Holder {
                var position: Point := Worker { x = 1 }
            }
            """, CheckMode.ALL);

        assertThat(parsedPackage.getResourceErrors()).isNotEmpty();
    }

    @Test
    void fieldValueOfAnotherTypeIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = true }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.TYPE_MISMATCH, "not assignable to field 'x'");
    }

    @Test
    void literalOfAnotherRecordIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 0 }
                redefine tran { position := Segment { from = position, to = position } }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.TYPE_MISMATCH, "Cannot assign");
    }

    @Test
    void classLevelLiteralOfRuntimeValuesIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var offset: int := 0
                var position: Point := Point { x = offset }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "RUNTIME");
    }

    @Test
    void literalIsNoWriteTarget() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 0 }
                redefine tran {
                    havoc(Point { x = 1 })
                    Point { x = 1 } := position
                    Point { x = 1 }.x := 2
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .hasSize(3)
                .allSatisfy(issue -> assertThat(issue.getCode()).isEqualTo(OxstsValidator.INCORRECT_ASSIGNMENT));
    }

    @Test
    void literalEvaluatedAtCompileTimeIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                refers same: bool = Point { x = 1 } == Point { x = 1 }
                redefine tran {
                    inline if (Point { x = 1 } != Point { x = 2 }) { }
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.UNSUPPORTED_FEATURE, "not yet evaluated at compile time");
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .extracting(Issue::getMessage)
                .anySatisfy(message -> assertThat(message).contains("a feature binding"))
                .anySatisfy(message -> assertThat(message).contains("an `inline if` guard"));
    }

    @Test
    void orderingComparisonWithLiteralIsRejected() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 0 }
                prop { return EF position < Point { x = 1 } }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "only be compared with == and !=");
    }

    @Test
    void recordNameIsNoValue() {
        var parsedPackage = parseHelper.parse(RECORDS + """
            class Holder {
                var position: Point := Point { x = 0 }
                redefine tran { position := Point {} }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertOnlyErrorsAre(parsedPackage, OxstsValidator.TYPE_MISMATCH, "'Point' is a record type, not a value");
    }

    private static void assertOnlyErrorsAre(WrappedOxstsPackage parsedPackage, String code, String messageSubstring) {
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(code);
                    assertThat(issue.getMessage()).contains(messageSubstring);
                });
    }

    private static void assertOnlyErrorsAreUnsupported(WrappedOxstsPackage parsedPackage, String messageSubstring) {
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.UNSUPPORTED_FEATURE);
                    assertThat(issue.getMessage()).contains(messageSubstring);
                });
    }
}
