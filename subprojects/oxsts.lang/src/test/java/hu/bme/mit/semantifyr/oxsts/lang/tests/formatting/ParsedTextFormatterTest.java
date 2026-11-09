/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.formatting;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import org.eclipse.xtext.formatting2.FormatterPreferenceKeys;
import org.eclipse.xtext.testing.formatter.FormatterTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@InjectWithOxsts
public class ParsedTextFormatterTest {

    @Inject
    private FormatterTestHelper formatterTestHelper;

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
    void spaceBeforeDeclarationColonIsKeptAsTheSerializerWritesIt() {
        assertFormatted("""
                package test

                class Node {
                    var x : int := 0
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
                            assume (true)
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
        formatterTestHelper.assertFormatted(request -> {
            request.setUseSerializer(false);
            request.getOrCreateMapBasedPreferences().put(FormatterPreferenceKeys.indentation, "    ");
            request.setToBeFormatted(toBeFormatted);
            request.setExpectation(expectation);
        });
    }
}
