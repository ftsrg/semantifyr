/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class TraceArgumentValidationTest {

    private static final String PORT = """
            package test
            class Port {
                var item: int := 0
                @Trace
                tran send(value: int) {
                    item := value
                }
                tran forward(value: int) {
                    item := value
                }
            }
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void runtimeArgumentOfATracedCallIsRejected() {
        var parsedPackage = parseHelper.parse(PORT + """
            class Host {
                var count: int := 0
                contains port: Port
                redefine tran {
                    inline port.send(count)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH);
                    assertThat(issue.getLineNumber()).isEqualTo(16);
                    assertThat(issue.getMessage()).contains("'send'");
                });
    }

    @Test
    void namedRuntimeArgumentOfATracedCallIsRejected() {
        var parsedPackage = parseHelper.parse(PORT + """
            class Host {
                var count: int := 0
                contains port: Port
                redefine tran {
                    inline port.send(value = count + 1)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertHasValidationIssue(OxstsValidator.EXPRESSION_MODALITY_TOO_HIGH, "'send'");
    }

    @Test
    void compileTimeArgumentsOfATracedCallPass() {
        var parsedPackage = parseHelper.parse(PORT + """
            class Host {
                refers limit: int = 3
                contains port: Port
                tran relay(value: int) {
                    inline port.send(value)
                }
                redefine tran {
                    inline port.send(1)
                    inline port.send(limit)
                    inline relay(2)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void runtimeArgumentOfAnUntracedCallPasses() {
        var parsedPackage = parseHelper.parse(PORT + """
            class Host {
                var count: int := 0
                contains port: Port
                redefine tran {
                    inline port.forward(count)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    @Test
    void recordArgumentOfATracedCallIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            record Point {
                var x: int := 0
            }
            class Port {
                var item: int := 0
                @Trace
                tran send(point: Point) {
                    item := point.x
                }
            }
            class Host {
                var position: Point := Point { x = 1 }
                contains port: Port
                tran relay(point: Point) {
                    inline port.send(point)
                }
                redefine tran {
                    inline port.send(Point { x = 2 })
                    inline port.send(position)
                    inline relay(position)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> issue.getSeverity() == Severity.ERROR)
                .hasSize(3)
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.UNSUPPORTED_FEATURE);
                    assertThat(issue.getMessage()).contains("Record arguments of the traced transition 'send'");
                });
    }
}
