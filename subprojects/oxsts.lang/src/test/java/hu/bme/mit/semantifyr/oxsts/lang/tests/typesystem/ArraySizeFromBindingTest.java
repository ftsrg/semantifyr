/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.typesystem;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.resource.XtextSyntaxDiagnostic;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class ArraySizeFromBindingTest {

    private static final String STORE = """
            package test
            record Message {
                var kind: int := 0
                var urgent: bool := false
            }
            class Item
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private ExpressionTypeEvaluatorProvider typeEvaluatorProvider;

    @Test
    void sizeFromABindingIsAnArrayIndexedAssignedAndComparedWithoutError() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                refers capacity: int
                var slots: int[capacity] := [0, ...]
                var backup: int[capacity] := [1, 2, ...]
                var index: int := 0
                var same: bool := false

                tran put(value: int) {
                    slots[capacity - 1] := value
                    slots[index] := slots[0]
                    backup := slots
                    same := slots == backup
                    havoc(slots)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void sizeFromABindingIsAnArrayEvenWhereItMayBeOne() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                refers capacity: int = 1
                var slots: int[capacity] := [0, ...]

                tran put(value: int) {
                    slots[0] := value
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
        var assignment = EcoreUtil2.getAllContentsOfType(parsedPackage.getOxstsPackage(), AssignmentOperation.class)
                .getFirst();
        var slots = ((IndexingSuffixExpression) assignment.getReference()).getPrimary();
        var slotsType = typeEvaluatorProvider.evaluate(slots);
        assertThat(slotsType.getRange().isPerInstance()).isTrue();
        assertThat(slotsType.getRange().isBounded()).isTrue();
        assertThat(slotsType.getRange().getLowerBound()).isEqualTo(1);
    }

    @Test
    void rangeWithABoundFromABindingAdmitsNothingInASlot() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Port {
                refers capacity: int
                contains first: Item
                var items: Item[0..capacity] := [nothing, ...]

                tran shift() {
                    items := [items[1], nothing]
                    items[0] := first
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void sizeWithoutALowerBoundOfNoneAdmitsNoNothing() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Port {
                refers capacity: int
                var items: Item[capacity] := [nothing, ...]
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "never nothing");
    }

    @Test
    void singleValueWhereAnArrayOfASizeFromABindingStandsIsRejected() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                refers capacity: int
                var slots: int[capacity] := 0
                var value: int := 0
                var same: bool := false

                tran read() {
                    value := slots
                    slots := value
                    slots := 7
                    same := slots == 0
                    same := 7 == slots
                }
            }
            """, CheckMode.ALL);

        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> {
                    return issue.getSeverity() == Severity.ERROR;
                })
                .hasSize(6)
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.TYPE_MISMATCH);
                });
    }

    @Test
    void literalOfOneValueAgainstASizeFromABindingIsAnArrayWhoseLengthTheCompilerChecks() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                refers capacity: int
                var slots: int[capacity] := [7]
                var same: bool := false

                tran reset() {
                    slots := [7]
                    same := slots == [7]
                    same := [7] != slots
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void literalOfOneValueStaysASingleValueForAMultiplicityWrittenAsOne() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Counter {
                var count: int[1] := [0]
                var optional: int[0..1] := [0]
                var plain: int := [0]

                tran step() {
                    count := count + 1
                    count := [3]
                    plain := count
                    optional := [count]
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void literalEndingInRepeatOutsideAnInitialValueIsRejected() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                refers capacity: int
                var slots: int[capacity] := [0, ...]
                var value: int := [0, ...]
                var same: bool := false

                tran reset() {
                    slots := [0, ...]
                    same := slots == [0, ...]
                }
            }
            """, CheckMode.ALL);

        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> {
                    return issue.getMessage().contains("takes its length from a declaration");
                })
                .hasSize(3);
    }

    @Test
    void literalEndingInRepeatTakesItsLengthFromALiteralSize() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                var filled: int[3] := [1, 2, 3, ...]
                var padded: int[3] := [1, ...]

                redefine tran {
                    var local: int[2] := [0, ...]
                    filled[0] := local[1]
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void prefixLongerThanALiteralSizeIsRejected() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                var slots: int[2] := [1, 2, 3, ...]
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "3 values for the 2 slots");
    }

    @Test
    void repeatWithoutAValueIsAParseError() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                var slots: int[2] := [...]
            }
            """);

        assertThat(parsedPackage.getResourceErrors()).singleElement().isInstanceOf(XtextSyntaxDiagnostic.class);
    }

    @Test
    void localArraySizedFromABindingIsRejected() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                refers capacity: int

                redefine tran {
                    var local: int[capacity] := [0, ...]
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(
                OxstsValidator.UNSUPPORTED_FEATURE, "local array 'local' must be a constant");
    }

    @Test
    void parameterSizedFromABindingIsAnArrayParameter() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                refers capacity: int

                tran put(values: int[capacity]) {
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "Array parameters");
    }

    @Test
    void sizeReadingAVariableIsRejected() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                var count: int := 2
                var slots: int[count] := [0, ...]
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH);
    }

    @Test
    void arrayOfRecordsInAClassVariableIsReadWrittenAndCopiedAtAnIndex() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                refers capacity: int
                var messages: Message[capacity] := [Message { kind = 0 }, ...]
                var fixed: Message[2] := [Message { kind = 1 }, Message { kind = 2 }]
                var value: Message := Message { kind = 3 }
                var index: int := 0

                tran put() {
                    messages[index].kind := 3
                    messages[index] := value
                    value := messages[0]
                    messages[index] := messages[index + 1]
                    fixed[1].urgent := messages[0].urgent
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void arrayOfRecordsAdmittingNothingIsRejected() {
        var parsedPackage = parseHelper.parse(STORE + """
            class Store {
                refers capacity: int
                var messages: Message[0..capacity] := [Message { kind = 0 }, ...]
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.UNSUPPORTED_FEATURE, "never nothing");
    }
}
