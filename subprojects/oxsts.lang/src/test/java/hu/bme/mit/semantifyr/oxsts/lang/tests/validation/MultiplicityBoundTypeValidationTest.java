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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@InjectWithOxsts
public class MultiplicityBoundTypeValidationTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "var values: int[true] := [0]",
                "var values: int[1..true] := [0]",
                "var values: int[false..2] := [0, 0]",
                "var elapsed: clock[true] := 0",
                "contains parts: Part[true]",
                "refers limit: int[\"two\"] = 2",
                "var weights: int[1.5] := [0]",
            })
    void aMultiplicityBoundThatIsNoIntegerIsItsOneErrorAndNoCheckFails(String declaration) {
        var loggedErrors = new ArrayList<LogEvent>();
        var parsedPackage = recordingValidatorErrors(loggedErrors, () -> {
            return parseHelper.parse("""
                    package test
                    class Part {
                    }
                    class Host {
                        %s
                    }
                    """.formatted(declaration), CheckMode.ALL);
        });

        assertThat(loggedErrors).isEmpty();
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getIssues())
                .filteredOn(issue -> {
                    return issue.getSeverity() == Severity.ERROR;
                })
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getMessage()).startsWith("A multiplicity bound must be an integer");
                    assertThat(issue.getCode()).isEqualTo(OxstsValidator.TYPE_MISMATCH);
                });
    }

    @Test
    void integerBoundsAndRangesPass() {
        var parsedPackage = parseHelper.parse("""
                package test
                class Part {
                }
                class Host {
                    refers capacity: int = 2
                    contains parts: Part[0..*]
                    var values: int[3] := [0, 0, 0]
                    var slots: int[0..<capacity] := [0, 0]
                    var flags: bool[1..2] := [false, false]
                }
                """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationIssues();
    }

    private static <T> T recordingValidatorErrors(List<LogEvent> loggedErrors, Supplier<T> action) {
        var logger = (Logger) LogManager.getLogger(OxstsValidator.class);
        var appender = new AbstractAppender("validator-errors", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel().isMoreSpecificThan(Level.ERROR)) {
                    loggedErrors.add(event.toImmutable());
                }
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            return action.get();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }
}
