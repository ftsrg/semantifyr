/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.formatting;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import org.eclipse.xtext.formatting2.FormatterRequest;
import org.eclipse.xtext.formatting2.IFormatter2;
import org.eclipse.xtext.formatting2.regionaccess.TextRegionAccessBuilder;
import org.eclipse.xtext.resource.XtextResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@InjectWithOxsts
public class ParsedTextFormatterTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private Provider<IFormatter2> formatter;

    @Inject
    private Provider<FormatterRequest> formatterRequest;

    @Inject
    private Provider<TextRegionAccessBuilder> regionAccessBuilder;

    @ParameterizedTest
    @ValueSource(strings = {"<", "<=", ">", ">=", "==", "!=", "+", "-", "*", "/", "&&", "||", "^^"})
    void binaryOperatorGetsOneSpaceOnEachSide(String operator) {
        assertFormatted("""
                package test

                class Node {
                    var x: int := 0

                    prop {
                        return x%1$sx
                    }
                }
                """.formatted(operator), """
                package test

                class Node {
                    var x: int := 0

                    prop {
                        return x %1$s x
                    }
                }
                """.formatted(operator));
    }

    @Test
    void declarationColonIsFollowedByOneSpace() {
        assertFormatted("""
                package test

                class Node {
                    contains children:Node[0..2]
                    var x:int := 0

                    tran step(amount:int) {
                        var y:int := amount
                    }

                    prop isPositive(value:int): bool {
                        return value > 0
                    }
                }
                """, """
                package test

                class Node {
                    contains children: Node[0..2]
                    var x: int := 0

                    tran step(amount: int) {
                        var y: int := amount
                    }

                    prop isPositive(value: int): bool {
                        return value > 0
                    }
                }
                """);
    }

    @Test
    void declarationColonAndParenthesesFollowTheNameDirectly() {
        assertFormatted("""
                package test

                annotation Marker (label : string)

                class Node {
                    var x : int := 0

                    tran step (amount : int) {
                        assume (x > 0)
                        havoc (x)
                        inline step (1)
                    }

                    prop isPositive (value : int) : bool {
                        return value > 0
                    }
                }

                @Marker ("node")
                class Marked {
                }
                """, """
                package test

                annotation Marker(label: string)

                class Node {
                    var x: int := 0

                    tran step(amount: int) {
                        assume(x > 0)
                        havoc(x)
                        inline step(1)
                    }

                    prop isPositive(value: int): bool {
                        return value > 0
                    }
                }

                @Marker("node")
                class Marked {
                }
                """);
    }

    @Test
    void literalsParenthesesAndReturnTypesAreWrittenCompactly() {
        assertFormatted("""
                package test

                enum Color { Red, Green ,Blue }

                class Node {
                    var values: int[3] := [1 ,2,  3]

                    prop first(): Node [ 0 .. 1 ] {
                        return nothing
                    }

                    prop {
                        return AG ( !( values[0] > 0 ) )
                    }
                }
                """, """
                package test

                enum Color {
                    Red,
                    Green,
                    Blue
                }

                class Node {
                    var values: int[3] := [1, 2, 3]

                    prop first(): Node[0..1] {
                        return nothing
                    }

                    prop {
                        return AG (!(values[0] > 0))
                    }
                }
                """);
    }

    @Test
    void keywordsGetOneSpace() {
        assertFormatted("""
                package   test

                abstract   class Node {
                    contains   first:   Node[0..1]   subsets   children
                    contains   children:   Node[0..*]
                    abstract   tran   step()
                    redefine   tran {
                    }
                }
                """, """
                package test

                abstract class Node {
                    contains first: Node[0..1] subsets children
                    contains children: Node[0..*]
                    abstract tran step()
                    redefine tran {
                    }
                }
                """);
    }

    @Test
    void elseIsSurroundedByOneSpace() {
        assertFormatted("""
                package test

                class Node {
                    var x: int := 0

                    redefine tran {
                        if (x < 3) {
                            x := x + 1
                        }else{
                            x := 0
                        }
                        inline if (true) {
                            x := 1
                        }else   {
                            x := 2
                        }
                    }
                }
                """, """
                package test

                class Node {
                    var x: int := 0

                    redefine tran {
                        if (x < 3) {
                            x := x + 1
                        } else {
                            x := 0
                        }
                        inline if (true) {
                            x := 1
                        } else {
                            x := 2
                        }
                    }
                }
                """);
    }

    @Test
    void blankLineSeparatesThePackageLineFromTheDeclarations() {
        assertFormatted("""
                package test
                class First {
                }
                class Second {
                }
                """, """
                package test

                class First {
                }
                class Second {
                }
                """);
    }

    @Test
    void membersKeepAtMostOneBlankLine() {
        assertFormatted("""
                package test

                class Node {
                    var x: int := 0
                    var y: int := 0



                    var z: int := 0
                }
                """, """
                package test

                class Node {
                    var x: int := 0
                    var y: int := 0

                    var z: int := 0
                }
                """);
    }

    @Test
    void blocksAreIndentedConsistently() {
        assertFormatted("""
                package test

                class Node {
                var x: int := 0
                        redefine tran {
                  choice {
                x := 1
                      } or {
                            x := 2
                  }
                }
                }
                """, """
                package test

                class Node {
                    var x: int := 0
                    redefine tran {
                        choice {
                            x := 1
                        } or {
                            x := 2
                        }
                    }
                }
                """);
    }

    @Test
    void commentOnTheLastLineOfABlockKeepsTheBlockIndentation() {
        assertFormatted("""
                package test

                class Node {
                    redefine tran {
                        choice {
                            assume(true)
                        } or {
                            // nothing happens
                        }
                    }
                }
                """);
    }

    private void assertFormatted(String toBeFormatted) {
        assertFormatted(toBeFormatted, toBeFormatted);
    }

    private void assertFormatted(String toBeFormatted, String expectation) {
        var parsedPackage = parseHelper.parse(toBeFormatted);
        parsedPackage.assertNoResourceErrors();
        var resource = (XtextResource) parsedPackage.getOxstsPackage().eResource();
        var request = formatterRequest
                .get()
                .setAllowIdentityEdits(true)
                .setTextRegionAccess(
                        regionAccessBuilder.get().forNodeModel(resource).create());

        var replacements = formatter.get().format(request);
        var formatted = request.getTextRegionAccess().getRewriter().renderToString(replacements);

        assertThat(formatted).isEqualTo(expectation);
    }
}
