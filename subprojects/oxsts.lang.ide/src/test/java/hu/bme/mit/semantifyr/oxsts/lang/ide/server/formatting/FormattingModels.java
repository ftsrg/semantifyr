/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.formatting;

final class FormattingModels {

    static final String UNFORMATTED = """
            package   example::format
            class Messy{var x:int:=0
            <|>tran   step( ){ if(x<3){x:=x+1}else{ x := 0 }
            choice { x:=1 } or {x:=2}
            }<|>
                prop{return AG(x>=0&&x<=3)}
            }
            """;

    static final String FORMATTED = """
            package example::format

            class Messy {
                var x: int := 0
                tran step() {
                    if (x < 3) {
                        x := x + 1
                    } else {
                        x := 0
                    }
                    choice {
                        x := 1
                    } or {
                        x := 2
                    }
                }
                prop {
                    return AG (x >= 0 && x <= 3)
                }
            }
            """;

    static final String TRANSITION_FORMATTED = """
            package   example::format
            class Messy{var x:int:=0
                tran step() {
                    if (x < 3) {
                        x := x + 1
                    } else {
                        x := 0
                    }
                    choice {
                        x := 1
                    } or {
                        x := 2
                    }
                }
                prop{return AG(x>=0&&x<=3)}
            }
            """;

    private FormattingModels() {
    }
}
