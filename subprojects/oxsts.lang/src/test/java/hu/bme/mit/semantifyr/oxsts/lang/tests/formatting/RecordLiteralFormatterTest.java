/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.formatting;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import org.eclipse.xtext.resource.SaveOptions;
import org.eclipse.xtext.serializer.ISerializer;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class RecordLiteralFormatterTest {

    private static final String RECORDS = """
            package test
            record Point {
                var x: int := 0
                var y: int := 0
            }
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private ISerializer serializer;

    @Test
    void literalOnOneLineGetsSpaces() {
        var formatted = format(RECORDS + """
            class Marker {
                var position: Point := Point{x=1,y=2}
            }
            """);

        assertThat(formatted).contains("var position: Point := Point { x = 1, y = 2 }");
    }

    @Test
    void literalOverSeveralLinesKeepsOneFieldPerLine() {
        var formatted = format(RECORDS + """
            class Marker {
                var position: Point := Point {
                x = 1,

                y = 2
                    }
            }
            """);

        assertThat(formatted).contains("    var position: Point := Point {\n        x = 1,\n        y = 2\n    }\n");
    }

    @Test
    void literalStartedOnSeveralLinesClosesOnALineOfItsOwn() {
        var formatted = format(RECORDS + """
            class Marker {
                var position: Point := Point { x = 1,
                    y = 2 }
                redefine tran {
                    position := Point {
                        x = 3 }
                }
            }
            """);

        assertThat(formatted).contains("    var position: Point := Point {\n        x = 1,\n        y = 2\n    }\n");
        assertThat(formatted).contains("        position := Point {\n            x = 3\n        }\n");
    }

    @Test
    void literalEndingARecordDeclarationLeavesItsClosingBraceOnALineOfItsOwn() {
        var formatted = format(RECORDS + """
            record Line {
                var start: Point := Point { x = 1 }
                var finish: Point := Point {
                    x = 2
                } }
            """);

        assertThat(formatted).contains("record Line {\n    var start: Point := Point { x = 1 }\n    var finish: Point := Point {\n        x = 2\n    }\n}\n");
    }

    private String format(String source) {
        var parsedPackage = parseHelper.parse(source);
        parsedPackage.assertNoResourceErrors();
        return serializer.serialize(
                parsedPackage.getOxstsPackage(),
                SaveOptions.newBuilder().format().getOptions());
    }
}
