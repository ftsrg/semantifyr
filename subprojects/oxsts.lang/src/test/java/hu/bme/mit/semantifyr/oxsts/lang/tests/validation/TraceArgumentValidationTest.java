/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.validation;

import static hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel;
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
    void runtimeArgumentsOfATracedCallPass() {
        var parsedPackage = parseHelper.parse(PORT + """
            class Host {
                var count: int := 0
                contains port: Port
                redefine tran {
                    inline port.send(count)
                    inline port.send(value = count + 1)
                }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
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
        var parsedPackage = parseHelper.parse(testModel(getClass(), "record_argument.semantifyr"), CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> {
                    return issue.getSeverity() == Severity.ERROR;
                })
                .hasSize(3)
                .allSatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.UNSUPPORTED_FEATURE);
                    assertThat(issue.getMessage()).contains("Record arguments of the traced transition 'send'");
                });
    }

    @Test
    void anArrayCannotReachATracedParameter() {
        var parsedPackage = parseHelper.parse(testModel(getClass(), "array_argument.semantifyr"), CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> {
                    return issue.getSeverity() == Severity.ERROR;
                })
                .extracting(issue -> {
                    return issue.getCode();
                })
                .containsExactlyInAnyOrder(
                        OxstsValidator.UNSUPPORTED_FEATURE, OxstsValidator.TYPE_MISMATCH, OxstsValidator.TYPE_MISMATCH);
    }

    @Test
    void runtimeRealAndStringArgumentsOfATracedCallPass() {
        var parsedPackage =
                parseHelper.parse(testModel(getClass(), "real_and_string_arguments.semantifyr"), CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }
}
